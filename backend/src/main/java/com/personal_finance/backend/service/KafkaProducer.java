package com.personal_finance.backend.service;

import org.springframework.stereotype.Service;
import com.personal_finance.backend.model.Transaction;
import org.springframework.kafka.core.KafkaTemplate;

@Service
public class KafkaProducer {
    private KafkaTemplate<String, Transaction> kafkaTemplate;

    // breaking here!, need to understand what serialisers are...
    public KafkaProducer(KafkaTemplate<String, Transaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public String addTransaction(Transaction transaction) {
        kafkaTemplate.send("additions", transaction);
        return "message sent successfully";
    }
}
