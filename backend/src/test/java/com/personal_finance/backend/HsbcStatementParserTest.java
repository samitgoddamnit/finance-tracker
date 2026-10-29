package com.personal_finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.HsbcStatementParser;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class HsbcStatementParserTest {

    // a real statement, kept out of git, so this test is skipped when it isn't there
    private static final Path STATEMENT = Path.of("../data/2026-09-03_Statement.pdf");

    private final HsbcStatementParser parser = new HsbcStatementParser();

    private List<Transaction> parseStatement() throws Exception {
        assumeTrue(Files.exists(STATEMENT), "sample statement not found at " + STATEMENT.toAbsolutePath());
        return parser.parse(Files.readAllBytes(STATEMENT));
    }

    // "Payments In £1,234.56" from the statement's Account Summary box, read straight from
    // the PDF so the test works for any statement and doesn't hard-code anyone's figures
    private static BigDecimal summaryFigure(String label) throws Exception {
        String text;
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(STATEMENT))) {
            text = new PDFTextStripper().getText(document);
        }
        Matcher matcher = Pattern.compile(label + "\\s*\\D?([\\d,]+\\.\\d{2})").matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No \"" + label + "\" in the statement's Account Summary");
        }
        return new BigDecimal(matcher.group(1).replace(",", ""));
    }

    @Test
    void totalsMatchTheAccountSummary() throws Exception {
        List<Transaction> transactions = parseStatement();

        BigDecimal paidIn = transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOut = transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(summaryFigure("Payments In"), paidIn);
        assertEquals(summaryFigure("Payments Out").negate(), paidOut);
    }

    @Test
    void readsCardPayment() throws Exception {
        Transaction first = parseStatement().get(0);

        assertEquals(new BigDecimal("-2.04"), first.getAmount());
        assertEquals("SAINSBURYS S/MKTS", first.getMerchant());
        assertEquals("SAINSBURYS S/MKTS CAMBRIDGE", first.getDescription());
        assertEquals(")))", first.getPaymentType());
        assertEquals("GBP", first.getCurrency());
        assertEquals("HSBC", first.getSource());
        assertEquals(LocalDate.of(2026, 8, 4).atStartOfDay(), first.getTime());
    }

    @Test
    void tellsRefundsApartFromPayments() throws Exception {
        List<Transaction> flix = parseStatement().stream()
                .filter(t -> "FLIX".equals(t.getMerchant()))
                .toList();

        // 20 Aug is a payment, 24 Aug is a refund of the same amount
        assertEquals(new BigDecimal("-11.49"), flix.get(0).getAmount());
        assertEquals(new BigDecimal("11.49"), flix.get(1).getAmount());
    }

    @Test
    void readsMoneyInFromBillPaymentLine() throws Exception {
        Transaction deposit = parseStatement().stream()
                .filter(t -> t.getDescription().contains("deposit"))
                .findFirst()
                .orElseThrow();

        assertEquals(new BigDecimal("260.00"), deposit.getAmount());
        assertEquals("BP", deposit.getPaymentType());
        assertEquals(LocalDate.of(2026, 8, 12).atStartOfDay(), deposit.getTime());
    }

    @Test
    void readsForeignCardPaymentAndItsFee() throws Exception {
        List<Transaction> transactions = parseStatement();
        int index = 0;
        while (!"PIXIV INC".equals(transactions.get(index).getMerchant())) {
            index++;
        }
        Transaction payment = transactions.get(index);
        Transaction fee = transactions.get(index + 1);

        assertEquals(new BigDecimal("-2.75"), payment.getAmount());
        assertEquals("VIS", payment.getPaymentType());
        assertEquals("INT'L 0055962090 PIXIV INC TOKYO JPY 590 @214.5454 Visa Rate", payment.getDescription());
        assertEquals(new BigDecimal("-0.07"), fee.getAmount());
        assertEquals("DR", fee.getPaymentType());
        assertEquals("Non-Sterling Transaction Fee", fee.getDescription());
    }

    @Test
    void datesCarryAcrossPageBreaks() throws Exception {
        // the first row on page 3 has no date of its own; it's still 24 Aug
        Transaction pageThreeFirst = parseStatement().stream()
                .filter(t -> t.getAmount().compareTo(new BigDecimal("-4.15")) == 0)
                .toList()
                .get(1);

        assertEquals(LocalDate.of(2026, 8, 24).atStartOfDay(), pageThreeFirst.getTime());
    }

    @Test
    void rejectsPdfThatIsNotAStatement() throws Exception {
        ByteArrayOutputStream blank = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(blank);
        }

        assertThrows(IllegalArgumentException.class, () -> parser.parse(blank.toByteArray()));
    }

    @Test
    void rejectsFileThatIsNotAPdf() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("not a pdf".getBytes()));
    }
}
