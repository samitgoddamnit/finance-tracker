package com.personal_finance.backend.service;

import java.io.IOException;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.repository.TransactionRepository;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final KafkaProducer prod;
    private final HsbcStatementParser statementParser;

    public TransactionService(
            TransactionRepository repository,
            KafkaProducer prod,
            HsbcStatementParser statementParser) {
        this.repository = repository;
        this.prod = prod;
        this.statementParser = statementParser;
    }

    public String addTransaction(Transaction transaction) {
        return prod.addTransaction(transaction);
    }

    public List<Transaction> getAllTransactions() {
        return repository.findAll();
    }

    public void removeAllTransactions() {
        repository.deleteAll();
    }

    public record ImportResult(List<Transaction> imported, int duplicatesSkipped) {
    }

    // parses an HSBC statement and sends each new transaction through kafka to be saved
    public ImportResult importStatement(MultipartFile file) throws IOException {
        List<Transaction> parsed = statementParser.parse(file.getBytes());
        List<Transaction> fresh = withoutDuplicates(parsed);
        fresh.forEach(this::addTransaction);
        return new ImportResult(fresh, parsed.size() - fresh.size());
    }

    // Skips transactions that are already saved, so uploading a statement twice, or two
    // statements that overlap by a few days, doesn't double anything up.
    // It compares counts rather than just checking "is this one already there?", because the
    // same day can genuinely have two identical rows (two £5.80 rounds at the same pub):
    // if the statement has two and one is saved, one more gets added.
    // (Transactions are saved asynchronously through kafka, so two uploads of the same file
    // within a second or so could still both get through.)
    private List<Transaction> withoutDuplicates(List<Transaction> parsed) {
        if (parsed.isEmpty()) {
            return parsed;
        }
        LocalDateTime from = parsed.stream().map(Transaction::getTime).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime to = parsed.stream().map(Transaction::getTime).max(Comparator.naturalOrder()).orElseThrow();

        Map<String, Integer> alreadySaved = new HashMap<>();
        for (Transaction saved : repository.findBySourceAndTimeBetween(parsed.get(0).getSource(), from, to)) {
            alreadySaved.merge(duplicateKey(saved), 1, Integer::sum);
        }

        List<Transaction> fresh = new ArrayList<>();
        for (Transaction transaction : parsed) {
            String key = duplicateKey(transaction);
            int remaining = alreadySaved.getOrDefault(key, 0);
            if (remaining > 0) {
                alreadySaved.put(key, remaining - 1);
            } else {
                fresh.add(transaction);
            }
        }
        return fresh;
    }

    private static String duplicateKey(Transaction transaction) {
        // setScale so 5.8 and 5.80 count as the same amount
        return transaction.getTime() + "|"
                + transaction.getAmount().setScale(2, RoundingMode.HALF_UP) + "|"
                + transaction.getDescription();
    }

}
