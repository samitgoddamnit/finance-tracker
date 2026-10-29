package com.personal_finance.backend.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.*;
import com.personal_finance.backend.service.DashboardService;
import com.personal_finance.backend.service.MonthlySummary;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/spent-this-month")
    public BigDecimal getSpentThisMonth() {
        return dashboard.spentThisMonth();
    }

    // money in and out per category per month, for the dashboard table
    @GetMapping("/monthly-summary")
    public MonthlySummary getMonthlySummary() {
        return dashboard.monthlySummary();
    }

}