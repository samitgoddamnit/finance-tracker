package com.personal_finance.backend.service;

import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.Transaction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// Categorises transactions by keyword rules from merchant-rules.csv,
// and works out the key used to remember merchants you've categorised.
// Both use the fields TransactionCleaner fills in.
@Service
public class MerchantMatcher {

    private static final String RULES_FILE = "merchant-rules.csv";

    private enum Direction { IN, OUT, ANY }

    private record Rule(Pattern pattern, Category category, Direction direction) {

        boolean matches(String text, Direction transactionDirection) {
            return (direction == Direction.ANY || direction == transactionDirection)
                    && pattern.matcher(text).find();
        }
    }

    private final List<Rule> rules;

    public MerchantMatcher() {
        this(new ClassPathResource(RULES_FILE));
    }

    MerchantMatcher(ClassPathResource rulesFile) {
        try (InputStream in = rulesFile.getInputStream()) {
            this.rules = readRules(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + rulesFile.getPath(), e);
        }
    }

    // Matches against the cleaned merchant and the payment's note, not the location,
    // so a shop in a town that happens to contain a keyword isn't caught by it.
    // Expects the transaction to have been through TransactionCleaner.
    public Optional<Category> matchRule(Transaction transaction) {
        String text = normalise(Objects.toString(transaction.getCleanMerchant(), "")
                + " " + Objects.toString(transaction.getReference(), ""));
        Direction direction = direction(transaction);
        return rules.stream()
                .filter(rule -> rule.matches(text, direction))
                .map(Rule::category)
                .findFirst();
    }

    // The cleaned merchant plus whether money went in or out: paying a friend and a
    // friend paying you back belong in different categories.
    public Optional<String> merchantKey(Transaction transaction) {
        if (transaction.getCleanMerchant() == null) {
            return Optional.empty();
        }
        return Optional.of(transaction.getCleanMerchant() + "|" + direction(transaction));
    }

    private static Direction direction(Transaction transaction) {
        return transaction.getAmount() != null && transaction.getAmount().signum() > 0
                ? Direction.IN
                : Direction.OUT;
    }

    // uppercase, with "*" separators turned into spaces: "SQ *ORICHA" -> "SQ ORICHA"
    private static String normalise(String text) {
        return text.toUpperCase(Locale.ROOT)
                .replace('*', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<Rule> readRules(InputStream in) throws IOException {
        List<Rule> rules = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new IllegalStateException(
                        RULES_FILE + " line " + lineNumber + " should be keyword,category,direction: " + line);
            }
            String keyword = normalise(parts[0]);
            // the keyword has to start at a word boundary, but can run on: SAINSBURY matches SAINSBURYS
            Pattern pattern = Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(keyword));
            try {
                rules.add(new Rule(pattern,
                        Category.valueOf(parts[1].strip()),
                        Direction.valueOf(parts[2].strip())));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        RULES_FILE + " line " + lineNumber + " has an unknown category or direction: " + line, e);
            }
        }
        return rules;
    }
}
