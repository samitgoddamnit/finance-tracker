package com.personal_finance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.CategorySource;
import com.personal_finance.backend.model.MerchantCategory;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.repository.MerchantCategoryRepository;
import com.personal_finance.backend.repository.TransactionRepository;
import com.personal_finance.backend.service.CategorisationMessages.CategorisationRequest;
import com.personal_finance.backend.service.CategorisationMessages.Prediction;
import com.personal_finance.backend.service.CategorisationMessages.RetrainRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// Decides each transaction's category. In order:
//   1. a merchant you've categorised yourself before (MERCHANT)
//   2. a keyword rule from merchant-rules.csv (RULE)
//   3. the ML service in ml-service/, which answers over Kafka (ML)
//   4. otherwise it stays Uncategorised (NONE)
// Categories you set on the website (USER) are never overwritten.
@Service
public class CategorisationService {

    private static final Logger log = LoggerFactory.getLogger(CategorisationService.class);
    private static final ObjectMapper json = new ObjectMapper();

    private final TransactionRepository transactions;
    private final MerchantCategoryRepository merchantCategories;
    private final MerchantMatcher matcher;
    private final TransactionCleaner cleaner;
    private final KafkaTemplate<String, Object> kafka;
    private final double confidenceThreshold;

    public CategorisationService(
            TransactionRepository transactions,
            MerchantCategoryRepository merchantCategories,
            MerchantMatcher matcher,
            TransactionCleaner cleaner,
            KafkaTemplate<String, Object> kafka,
            @Value("${categorisation.ml-confidence-threshold:0.5}") double confidenceThreshold) {
        this.transactions = transactions;
        this.merchantCategories = merchantCategories;
        this.matcher = matcher;
        this.cleaner = cleaner;
        this.kafka = kafka;
        this.confidenceThreshold = confidenceThreshold;
    }

    // runs on every newly saved transaction
    public void categorise(Transaction transaction) {
        cleaner.clean(transaction);
        // a category sent in with the transaction (e.g. through POST /transactions) counts as yours
        if (transaction.getCategory() != null && transaction.getCategorySource() == null) {
            transaction.setCategorySource(CategorySource.USER);
            transactions.save(transaction);
            return;
        }
        if (applyMerchantOrRule(transaction)) {
            transactions.save(transaction);
            return;
        }
        transaction.categorise(Category.UNCATEGORISED, CategorySource.NONE, null);
        transactions.save(transaction);
        kafka.send(CategorisationMessages.REQUESTS_TOPIC, new CategorisationRequest(
                transaction.getTransactionID(),
                transaction.getCleanMerchant(),
                transaction.getReference(),
                transaction.getPaymentType(),
                transaction.getAmount()));
    }

    // You picked a category on the website. Remember it for this merchant,
    // apply it to the merchant's other transactions, and have the ML service retrain.
    // (Not one database transaction on purpose: the saves must be committed before the
    // retrain message goes out, because the ML service reads its training data from the database.)
    public Transaction setByUser(long transactionId, Category category) {
        Transaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("No transaction with id " + transactionId));
        transaction.categorise(category, CategorySource.USER, null);
        transactions.save(transaction);

        Optional<String> key = matcher.merchantKey(transaction);
        if (key.isPresent()) {
            merchantCategories.save(new MerchantCategory(key.get(), category));
            for (Transaction other : transactions.findAll()) {
                if (other.getCategorySource() != CategorySource.USER
                        && key.equals(matcher.merchantKey(other))) {
                    other.categorise(category, CategorySource.MERCHANT, null);
                    transactions.save(other);
                }
            }
        }

        requestRetrain("category set to " + category + " for transaction " + transactionId);
        return transaction;
    }

    // Re-cleans every transaction and re-runs merchant matching and rules over everything
    // you haven't categorised yourself, e.g. after editing merchant-rules.csv or the cleaner.
    // The ML service then retrains on the result and predicts whatever is still left.
    public CategorisationSummary recategoriseAll() {
        List<Transaction> all = transactions.findAll();
        for (Transaction transaction : all) {
            cleaner.clean(transaction);
            transactions.save(transaction);
        }
        refreshMerchantMemory(all);

        int matched = 0;
        int leftForMl = 0;
        for (Transaction transaction : all) {
            if (transaction.getCategorySource() == CategorySource.USER) {
                continue;
            }
            if (applyMerchantOrRule(transaction)) {
                matched++;
            } else {
                transaction.categorise(Category.UNCATEGORISED, CategorySource.NONE, null);
                leftForMl++;
            }
            transactions.save(transaction);
        }
        requestRetrain("recategorised all transactions");
        return new CategorisationSummary(matched, leftForMl);
    }

    public record CategorisationSummary(int matchedByMerchantOrRule, int sentToMl) {
    }

    // Merchant memory records your own choices, so it's refreshed from them here. This matters
    // when the cleaner changes: a merchant's key may come out differently. Entries are only
    // added or updated, never removed, because your choices outlive their transactions:
    // after emptying the table and re-uploading, the memory is all that's left of them.
    private void refreshMerchantMemory(List<Transaction> all) {
        all.stream()
                .filter(transaction -> transaction.getCategorySource() == CategorySource.USER)
                // later choices win if you categorised the same merchant twice
                .sorted(Comparator.comparingLong(Transaction::getTransactionID))
                .forEach(transaction -> matcher.merchantKey(transaction).ifPresent(key ->
                        merchantCategories.save(new MerchantCategory(key, transaction.getCategory()))));
    }

    @KafkaListener(
            topics = CategorisationMessages.PREDICTIONS_TOPIC,
            groupId = "categoriser",
            // the ML service sends plain JSON without Spring's type headers, so parse it ourselves
            properties = "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer")
    public void onPrediction(String message) throws Exception {
        applyPrediction(json.readValue(message, Prediction.class));
    }

    void applyPrediction(Prediction prediction) {
        Optional<Transaction> found = transactions.findById(prediction.transactionId());
        if (found.isEmpty()) {
            return;
        }
        Transaction transaction = found.get();
        CategorySource source = transaction.getCategorySource();
        // something better already decided while the ML service was thinking
        if (source != null && source.isLabelledData()) {
            return;
        }

        Category category;
        try {
            category = Category.valueOf(prediction.category());
        } catch (IllegalArgumentException e) {
            log.warn("ML service predicted an unknown category: {}", prediction.category());
            return;
        }

        boolean moneyIn = transaction.getAmount() != null && transaction.getAmount().signum() > 0;
        if (prediction.confidence() >= confidenceThreshold
                && category != Category.UNCATEGORISED
                // e.g. a friend paying you back can't be a "transfer out", however sure the model is
                && category.fitsDirection(moneyIn)) {
            transaction.categorise(category, CategorySource.ML, prediction.confidence());
        } else {
            transaction.categorise(Category.UNCATEGORISED, CategorySource.NONE, null);
        }
        transactions.save(transaction);
    }

    private boolean applyMerchantOrRule(Transaction transaction) {
        Optional<Category> remembered = matcher.merchantKey(transaction)
                .flatMap(merchantCategories::findById)
                .map(MerchantCategory::getCategory);
        if (remembered.isPresent()) {
            transaction.categorise(remembered.get(), CategorySource.MERCHANT, null);
            return true;
        }
        Optional<Category> rule = matcher.matchRule(transaction);
        if (rule.isPresent()) {
            transaction.categorise(rule.get(), CategorySource.RULE, null);
            return true;
        }
        return false;
    }

    private void requestRetrain(String reason) {
        kafka.send(CategorisationMessages.RETRAIN_TOPIC, new RetrainRequest(reason));
    }
}
