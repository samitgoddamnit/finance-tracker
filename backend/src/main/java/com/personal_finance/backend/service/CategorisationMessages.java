package com.personal_finance.backend.service;

import java.math.BigDecimal;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The Kafka topics and messages shared with the ML service in ml-service/.
// Messages are JSON, so the field names here have to match the Python side.
@Configuration
public class CategorisationMessages {

    // backend -> ML: please categorise this transaction
    public static final String REQUESTS_TOPIC = "category-requests";
    // backend -> ML: the labelled data changed, retrain and re-predict
    public static final String RETRAIN_TOPIC = "category-retrain";
    // ML -> backend: here's my prediction
    public static final String PREDICTIONS_TOPIC = "category-predictions";

    public record CategorisationRequest(
            long transactionId, String cleanMerchant, String reference, String paymentType, BigDecimal amount) {
    }

    public record RetrainRequest(String reason) {
    }

    public record Prediction(long transactionId, String category, double confidence) {
    }

    // created on startup if they don't exist yet
    @Bean
    NewTopic requestsTopic() {
        return new NewTopic(REQUESTS_TOPIC, 1, (short) 1);
    }

    @Bean
    NewTopic retrainTopic() {
        return new NewTopic(RETRAIN_TOPIC, 1, (short) 1);
    }

    @Bean
    NewTopic predictionsTopic() {
        return new NewTopic(PREDICTIONS_TOPIC, 1, (short) 1);
    }
}
