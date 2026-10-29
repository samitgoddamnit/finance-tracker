package com.personal_finance.backend.service;

import com.personal_finance.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.YearMonth;

@Service
public class DashboardService {
    private final TransactionRepository transactions;

    public DashboardService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    // same rules as the monthly table: money out, refunds taken off, income left out
    public BigDecimal spentThisMonth() {
        return monthlySummary().spentIn(YearMonth.now());
    }

    public MonthlySummary monthlySummary() {
        return MonthlySummary.from(transactions.findAll());
    }
}
