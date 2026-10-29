package com.personal_finance.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.personal_finance.backend.model.Transaction;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findBySourceAndTimeBetween(String source, LocalDateTime from, LocalDateTime to);
}
