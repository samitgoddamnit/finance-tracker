package com.personal_finance.backend.model;

import jakarta.persistence.*;

// Remembers the category you gave a merchant, so its other transactions follow.
@Entity
@Table(name = "merchant_categories")
public class MerchantCategory {

    // normalised merchant name, see MerchantMatcher.merchantKey
    @Id
    private String merchantKey;

    @Enumerated(EnumType.STRING)
    private Category category;

    protected MerchantCategory() {
    }

    public MerchantCategory(String merchantKey, Category category) {
        this.merchantKey = merchantKey;
        this.category = category;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
