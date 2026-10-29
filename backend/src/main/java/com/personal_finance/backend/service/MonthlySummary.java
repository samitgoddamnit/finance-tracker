package com.personal_finance.backend.service;

import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.Transaction;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

// Money in and money out per category, month by month, shaped as tables:
// one row per category, one column per month. All amounts are positive.
//
// Money in is Income, Money from friends, and anything uncategorised that came in.
// Everything else is money out. Refunds count against the category they refund,
// so a refunded coach ticket cancels out rather than showing up as money in.
public record MonthlySummary(
        List<String> months,
        List<Row> moneyIn,
        List<BigDecimal> inTotals,
        List<Row> moneyOut,
        List<BigDecimal> outTotals) {

    public record Row(String category, String label, List<BigDecimal> amounts, BigDecimal total) {
    }

    // total spent in one month, or zero if there's nothing for that month
    public BigDecimal spentIn(YearMonth month) {
        int index = months.indexOf(month.toString());
        return index < 0 ? BigDecimal.ZERO : outTotals.get(index);
    }

    public static MonthlySummary from(List<Transaction> transactions) {
        Map<Category, Map<YearMonth, BigDecimal>> in = new EnumMap<>(Category.class);
        Map<Category, Map<YearMonth, BigDecimal>> out = new EnumMap<>(Category.class);
        TreeSet<YearMonth> months = new TreeSet<>();

        for (Transaction transaction : transactions) {
            if (transaction.getTime() == null || transaction.getAmount() == null) {
                continue;
            }
            Category category = transaction.getCategory() == null
                    ? Category.UNCATEGORISED
                    : transaction.getCategory();
            boolean cameIn = transaction.getAmount().signum() > 0;
            YearMonth month = YearMonth.from(transaction.getTime());
            months.add(month);

            if (category.isMoneyIn() || (category == Category.UNCATEGORISED && cameIn)) {
                add(in, category, month, transaction.getAmount());
            } else {
                add(out, category, month, transaction.getAmount().negate());
            }
        }

        List<YearMonth> columns = new ArrayList<>(months);
        List<Row> inRows = rows(in, columns);
        List<Row> outRows = rows(out, columns);
        return new MonthlySummary(
                columns.stream().map(YearMonth::toString).toList(),
                inRows,
                totals(inRows, columns.size()),
                outRows,
                totals(outRows, columns.size()));
    }

    private static void add(Map<Category, Map<YearMonth, BigDecimal>> side,
            Category category, YearMonth month, BigDecimal amount) {
        side.computeIfAbsent(category, c -> new TreeMap<>()).merge(month, amount, BigDecimal::add);
    }

    private static List<Row> rows(Map<Category, Map<YearMonth, BigDecimal>> side, List<YearMonth> months) {
        List<Row> rows = new ArrayList<>();
        side.forEach((category, byMonth) -> {
            List<BigDecimal> amounts = months.stream()
                    .map(month -> byMonth.getOrDefault(month, BigDecimal.ZERO))
                    .toList();
            BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new Row(category.name(), category.getLabel(), amounts, total));
        });
        // biggest first, with Uncategorised always at the bottom
        rows.sort(Comparator
                .comparing((Row row) -> row.category().equals(Category.UNCATEGORISED.name()))
                .thenComparing(Row::total, Comparator.reverseOrder()));
        return rows;
    }

    private static List<BigDecimal> totals(List<Row> rows, int monthCount) {
        List<BigDecimal> totals = new ArrayList<>();
        for (int i = 0; i < monthCount; i++) {
            int month = i;
            totals.add(rows.stream().map(row -> row.amounts().get(month)).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return totals;
    }
}
