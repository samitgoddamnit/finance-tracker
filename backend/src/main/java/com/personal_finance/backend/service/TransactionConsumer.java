package com.personal_finance.backend;

import org.springframework.stereotype.Service;
import com.personal_finance.backend.repository.TransactionRepository;
import com.personal_finance.backend.service.CategorisationService;
import com.personal_finance.backend.model.Transaction;
import org.springframework.kafka.annotation.KafkaListener;

@Service
public class TransactionConsumer {
    private final TransactionRepository repository;
    private final CategorisationService categorisation;

    public TransactionConsumer(TransactionRepository repository, CategorisationService categorisation) {
        this.repository = repository;
        this.categorisation = categorisation;
    }

    @KafkaListener(topics = "additions", groupId = "transaction-group")
    public void consumeTransaction(Transaction transaction) {
        Transaction saved = repository.save(transaction);
        categorisation.categorise(saved);
    }
    // stores the transactions in the database, then works out their category
}
