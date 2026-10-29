package com.personal_finance.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// This is the entity class — what gets stored in the database.
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long transactionID;

    private BigDecimal amount;
    private String currency;
    private String merchant;
    private String description;
    private String source;
    // the bank's code for how the payment was made, e.g. VIS (card), ))) (contactless), BP (bill payment), CR (credit)
    private String paymentType;
    // tidied-up versions of the text above, worked out by TransactionCleaner
    private String cleanMerchant;
    private String location;
    private String reference;
    @Enumerated(EnumType.STRING)
    private Category category;
    @Enumerated(EnumType.STRING)
    private CategorySource categorySource;
    // how sure the ML service was, from 0 to 1; only set when categorySource is ML
    private Double categoryConfidence;
    private LocalDateTime time;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getCleanMerchant() {
        return cleanMerchant;
    }

    public void setCleanMerchant(String cleanMerchant) {
        this.cleanMerchant = cleanMerchant;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(CategorySource categorySource) {
        this.categorySource = categorySource;
    }

    public Double getCategoryConfidence() {
        return categoryConfidence;
    }

    public void setCategoryConfidence(Double categoryConfidence) {
        this.categoryConfidence = categoryConfidence;
    }

    // sets the category along with where it came from, clearing any old ML confidence
    public void categorise(Category category, CategorySource source, Double confidence) {
        this.category = category;
        this.categorySource = source;
        this.categoryConfidence = confidence;
    }

    public long getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(long transactionID) {
        this.transactionID = transactionID;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "Transaction { amount=" + amount
                + ", currency=" + currency
                + ", merchant=" + merchant
                + ", description=" + description
                + ", paymentType=" + paymentType
                + ", category=" + category
                + ", categorySource=" + categorySource
                + "}";
    }
}