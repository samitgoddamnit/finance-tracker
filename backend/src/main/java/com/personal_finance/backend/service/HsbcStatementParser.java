package com.personal_finance.backend.service;

import com.personal_finance.backend.model.Transaction;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

// Turns an HSBC UK current account statement PDF into Transaction objects.
//
// The plain text of a statement can't tell a payment from a refund (both are
// just "VIS FLIX Bristol 11.49"), so this reads the x-position of every word and
// works out which column it sits in, using the table header on each page.
// Every running balance printed on the statement is checked against the parsed
// amounts, so a statement that doesn't reconcile is rejected rather than saved.
@Service
public class HsbcStatementParser {

    private static final String SOURCE = "HSBC";
    private static final String CURRENCY = "GBP";

    private static final Pattern DATE = Pattern.compile("\\d{2} [A-Za-z]{3} \\d{2}");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,3}(,\\d{3})*\\.\\d{2}");
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM yy")
            .toFormatter(Locale.ENGLISH);

    public List<Transaction> parse(byte[] pdfBytes) throws IOException {
        List<List<Word>> lines;
        try (PDDocument document = loadPdf(pdfBytes)) {
            lines = new LineCollector().collect(document);
        }
        return new StatementReader().read(lines);
    }

    // a file that isn't a readable PDF is a bad upload, not a server error
    private static PDDocument loadPdf(byte[] pdfBytes) {
        try {
            return Loader.loadPDF(pdfBytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read this file as a PDF: " + e.getMessage(), e);
        }
    }

    // A chunk of text on the page and its horizontal extent.
    record Word(String text, float left, float right) {
    }

    // Where the columns of the transaction table are, taken from its header row:
    // "Date | Payment type and details | Paid out | Paid in | Balance".
    private record Columns(float typeLeft, float paidOutRight, float paidInRight, float balanceRight) {

        static Columns fromHeader(List<Word> line) {
            if (line.isEmpty() || !line.get(0).text().equals("Date")) {
                return null;
            }
            Float typeLeft = null, paidOutRight = null, paidInRight = null, balanceRight = null;
            for (Word word : line) {
                switch (word.text()) {
                    case "Paym" -> typeLeft = word.left();
                    case "out" -> paidOutRight = word.right();
                    case "in" -> paidInRight = word.right();
                    case "Balance" -> balanceRight = word.right();
                    default -> { }
                }
            }
            if (typeLeft == null || paidOutRight == null || paidInRight == null || balanceRight == null) {
                return null;
            }
            return new Columns(typeLeft, paidOutRight, paidInRight, balanceRight);
        }

        boolean isDateColumn(Word word) {
            return word.left() < typeLeft - 10;
        }

        boolean isTypeColumn(Word word) {
            return word.left() < typeLeft + 15;
        }

        // Amounts are right-aligned, so their right edge sits near the header's.
        boolean isAmountColumn(Word word) {
            return word.right() > paidOutRight - 30;
        }

        Column amountColumn(Word word) {
            float toOut = Math.abs(word.right() - paidOutRight);
            float toIn = Math.abs(word.right() - paidInRight);
            float toBalance = Math.abs(word.right() - balanceRight);
            if (toOut <= toIn && toOut <= toBalance) {
                return Column.PAID_OUT;
            }
            return toIn <= toBalance ? Column.PAID_IN : Column.BALANCE;
        }
    }

    private enum Column { PAID_OUT, PAID_IN, BALANCE }

    // Walks the table rows. A transaction starts at a payment type code (VIS, ))), BP, CR...),
    // collects description lines, and ends at its paid out / paid in amount. Rows without a
    // date belong to the most recent date above them, even across a page break.
    private static class StatementReader {
        private final List<Transaction> transactions = new ArrayList<>();
        private Columns columns;
        private LocalDate date;
        private BigDecimal balance;
        private String pendingType;
        private List<String> pendingDetails;

        List<Transaction> read(List<List<Word>> lines) {
            boolean foundTable = false;
            for (List<Word> line : lines) {
                Columns header = Columns.fromHeader(line);
                if (header != null) {
                    columns = header;
                    foundTable = true;
                } else if (columns != null) {
                    readRow(line);
                }
            }
            if (!foundTable) {
                throw new IllegalArgumentException("No HSBC transaction table found in this PDF");
            }
            if (pendingType != null) {
                throw new IllegalArgumentException(
                        "Statement ended part-way through a transaction: " + String.join(" ", pendingDetails));
            }
            return transactions;
        }

        private void readRow(List<Word> line) {
            String text = joinText(line);
            boolean broughtForward = text.contains("BALANCE BROUGHT FORWARD");
            boolean carriedForward = text.contains("BALANCE CARRIED FORWARD");

            String type = null;
            List<String> details = new ArrayList<>();
            List<Word> amounts = new ArrayList<>();
            for (Word word : line) {
                if (columns.isAmountColumn(word) && AMOUNT.matcher(word.text()).matches()) {
                    amounts.add(word);
                } else if (columns.isDateColumn(word)) {
                    if (DATE.matcher(word.text()).matches()) {
                        date = LocalDate.parse(word.text(), DATE_FORMAT);
                    }
                } else if (columns.isTypeColumn(word) && type == null && details.isEmpty()) {
                    type = word.text();
                } else {
                    details.add(word.text());
                }
            }

            if (broughtForward || carriedForward) {
                for (Word amount : amounts) {
                    if (columns.amountColumn(amount) == Column.BALANCE) {
                        checkBalance(parseAmount(amount), text);
                    }
                }
                if (carriedForward) {
                    // Everything below this on the page is small print, not transactions.
                    columns = null;
                }
                return;
            }

            if (type != null) {
                if (pendingType != null) {
                    throw new IllegalArgumentException(
                            "Transaction has no amount: " + String.join(" ", pendingDetails));
                }
                pendingType = type;
                pendingDetails = new ArrayList<>();
            }
            if (pendingType != null && !details.isEmpty()) {
                pendingDetails.add(String.join(" ", details));
            }

            for (Word amount : amounts) {
                Column column = columns.amountColumn(amount);
                if (column == Column.BALANCE) {
                    checkBalance(parseAmount(amount), text);
                } else {
                    finishTransaction(parseAmount(amount), column == Column.PAID_IN, text);
                }
            }
        }

        private void finishTransaction(BigDecimal amount, boolean paidIn, String row) {
            if (pendingType == null) {
                throw new IllegalArgumentException("Found an amount with no transaction: " + row);
            }
            if (date == null) {
                throw new IllegalArgumentException("Found a transaction before any date: " + row);
            }
            BigDecimal signed = paidIn ? amount : amount.negate();

            Transaction transaction = new Transaction();
            transaction.setAmount(signed);
            transaction.setCurrency(CURRENCY);
            transaction.setPaymentType(pendingType);
            transaction.setMerchant(merchant(pendingDetails));
            transaction.setDescription(String.join(" ", pendingDetails));
            transaction.setSource(SOURCE);
            transaction.setTime(date.atStartOfDay());
            transactions.add(transaction);

            if (balance != null) {
                balance = balance.add(signed);
            }
            pendingType = null;
            pendingDetails = null;
        }

        private void checkBalance(BigDecimal printed, String row) {
            if (balance == null) {
                balance = printed;
            } else if (balance.compareTo(printed) != 0) {
                throw new IllegalArgumentException("Statement doesn't add up: expected balance "
                        + balance + " but the statement shows " + printed + " at \"" + row + "\"");
            }
        }

        // The first detail line is the merchant or payee, except for foreign card payments
        // ("VIS INT'L 0055962090 / PIXIV INC / TOKYO ..."), where it's a reference number.
        private static String merchant(List<String> details) {
            if (details.isEmpty()) {
                return null;
            }
            if (details.get(0).startsWith("INT'L") && details.size() > 1) {
                return details.get(1);
            }
            return details.get(0);
        }

        private static BigDecimal parseAmount(Word word) {
            return new BigDecimal(word.text().replace(",", ""));
        }

        private static String joinText(List<Word> line) {
            return String.join(" ", line.stream().map(Word::text).toList());
        }
    }

    // Collects the words on each line of the PDF, in reading order, with their positions.
    private static class LineCollector extends PDFTextStripper {
        private final List<List<Word>> lines = new ArrayList<>();
        private List<Word> line = new ArrayList<>();

        LineCollector() {
            setSortByPosition(true);
        }

        List<List<Word>> collect(PDDocument document) throws IOException {
            getText(document);
            endLine();
            return lines;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            String cleaned = text.replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty() || positions.isEmpty()) {
                return;
            }
            TextPosition first = positions.get(0);
            TextPosition last = positions.get(positions.size() - 1);
            line.add(new Word(cleaned, first.getXDirAdj(), last.getXDirAdj() + last.getWidthDirAdj()));
        }

        @Override
        protected void writeLineSeparator() {
            endLine();
        }

        @Override
        protected void endPage(PDPage page) {
            endLine();
        }

        private void endLine() {
            if (!line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
            }
        }
    }
}
