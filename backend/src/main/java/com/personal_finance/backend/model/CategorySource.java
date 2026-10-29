package com.personal_finance.backend.model;

// Where a transaction's category came from, strongest first.
// Anything below USER can be overwritten when the categoriser runs again.
public enum CategorySource {
    // you picked it on the website
    USER,
    // same merchant as a transaction you categorised yourself
    MERCHANT,
    // matched a keyword in merchant-rules.csv
    RULE,
    // predicted by the ML service
    ML,
    // nothing could place it
    NONE;

    public boolean isLabelledData() {
        return this == USER || this == MERCHANT || this == RULE;
    }
}
