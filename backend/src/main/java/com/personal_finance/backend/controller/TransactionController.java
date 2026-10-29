package com.personal_finance.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.KafkaProducer;
import com.personal_finance.backend.service.TransactionService;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service, KafkaProducer kafkaProd) {
        this.service = service;
    }

    // CREATE (POST)
    @PostMapping
    public String addTransaction(@RequestBody Transaction transaction) {
        // here, I receive raw data to add to the database.
        return service.addTransaction(transaction);
    }

    // READ (GET ALL)
    @GetMapping
    public List<Transaction> getAllTransactions() {
        return service.getAllTransactions();
    }

    @PostMapping("/upload")
    public TransactionService.ImportResult uploadPDF(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        return service.importStatement(file);
    }

    @PostMapping("/bulk")
    public List<String> addTransactions(@RequestBody List<Transaction> transactions) {
        return transactions.stream()
                .map(service::addTransaction)
                .toList();
    }

    @DeleteMapping
    public void removeAllTransactions() {
        service.removeAllTransactions();
    }

    // sends upload problems (wrong file type, statement that doesn't parse) back as a readable 400
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

}
