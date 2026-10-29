package com.personal_finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.MonthlySummary;
import com.personal_finance.backend.service.MonthlySummary.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthlySummaryTest {

    private static Transaction transaction(String date, String amount, Category category) {
        Transaction transaction = new Transaction();
        transaction.setTime(LocalDate.parse(date).atStartOfDay());
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory(category);
        return transaction;
    }

    private static Row row(List<Row> rows, Category category) {
        return rows.stream()
                .filter(row -> row.category().equals(category.name()))
                .findFirst()
                .orElseThrow();
    }

    private static List<BigDecimal> amounts(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void totalsMoneyOutPerCategoryPerMonth() {
        MonthlySummary summary = MonthlySummary.from(List.of(
                transaction("2026-08-04", "-2.04", Category.GROCERIES),
                transaction("2026-08-24", "-3.30", Category.GROCERIES),
                transaction("2026-09-02", "-15.00", Category.PHONE_AND_BILLS),
                transaction("2026-09-03", "-6.00", Category.TRANSPORT)));

        assertEquals(List.of("2026-08", "2026-09"), summary.months());
        assertEquals(amounts("5.34", "0"), row(summary.moneyOut(), Category.GROCERIES).amounts());
        assertEquals(amounts("0", "15.00"), row(summary.moneyOut(), Category.PHONE_AND_BILLS).amounts());
        assertEquals(amounts("5.34", "21.00"), summary.outTotals());
    }

    @Test
    void totalsMoneyInSeparately() {
        MonthlySummary summary = MonthlySummary.from(List.of(
                transaction("2026-08-28", "412.20", Category.INCOME),
                transaction("2026-09-03", "38.00", Category.MONEY_FROM_FRIENDS),
                transaction("2026-09-03", "3.00", Category.UNCATEGORISED),
                transaction("2026-09-03", "-20.00", Category.UNCATEGORISED)));

        assertEquals(amounts("412.20", "0"), row(summary.moneyIn(), Category.INCOME).amounts());
        assertEquals(amounts("0", "38.00"), row(summary.moneyIn(), Category.MONEY_FROM_FRIENDS).amounts());
        // uncategorised money coming in is money in; going out is money out
        assertEquals(amounts("0", "3.00"), row(summary.moneyIn(), Category.UNCATEGORISED).amounts());
        assertEquals(amounts("0", "20.00"), row(summary.moneyOut(), Category.UNCATEGORISED).amounts());
        assertEquals(amounts("412.20", "41.00"), summary.inTotals());
    }

    @Test
    void refundsCountAgainstTheirCategoryNotAsMoneyIn() {
        MonthlySummary summary = MonthlySummary.from(List.of(
                transaction("2026-08-20", "-11.49", Category.TRANSPORT),
                transaction("2026-08-24", "11.49", Category.TRANSPORT),
                transaction("2026-08-25", "-9.99", Category.TRANSPORT)));

        assertEquals(amounts("9.99"), row(summary.moneyOut(), Category.TRANSPORT).amounts());
        assertEquals(List.of(), summary.moneyIn());
        assertEquals(amounts("0"), summary.inTotals());
    }

    @Test
    void biggestFirstAndUncategorisedLast() {
        MonthlySummary summary = MonthlySummary.from(List.of(
                transaction("2026-08-01", "-500.00", Category.UNCATEGORISED),
                transaction("2026-08-01", "-5.00", Category.GROCERIES),
                transaction("2026-08-01", "-50.00", Category.EATING_OUT)));

        assertEquals(
                List.of("EATING_OUT", "GROCERIES", "UNCATEGORISED"),
                summary.moneyOut().stream().map(Row::category).toList());
    }

    @Test
    void spentInAMonthWithNothingIsZero() {
        MonthlySummary summary = MonthlySummary.from(List.of(
                transaction("2026-09-02", "-15.00", Category.PHONE_AND_BILLS)));

        assertEquals(new BigDecimal("15.00"), summary.spentIn(YearMonth.of(2026, 9)));
        assertEquals(BigDecimal.ZERO, summary.spentIn(YearMonth.of(2026, 10)));
    }
}
