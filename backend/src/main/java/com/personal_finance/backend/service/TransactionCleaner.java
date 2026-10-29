package com.personal_finance.backend.service;

import com.personal_finance.backend.model.Transaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

// Works out tidy versions of a transaction's bank text, stored alongside the raw text:
//
//   cleanMerchant  who was paid, without reference codes, processors or company suffixes:
//                  "Spotify P45CD4F0E5" and "Spotify Limited" both become "SPOTIFY"
//   location       for card payments, where it happened: "COVENTRY", "amazon.co.uk"
//   reference      for everything else, the note on the payment: "hello chat", "for shopping"
//
// Merchant memory, the keyword rules and the ML service all read these fields,
// so they all see a transaction the same way.
@Service
public class TransactionCleaner {

    // payment types where the line after the merchant is a place, not a note
    private static final Set<String> CARD_PAYMENT_TYPES = Set.of("VIS", ")))", "ATM");

    // card processors that put their own name in front: "SQ *ORICHA" is a shop called Oricha
    // paid through Square. Only removed when followed by "*", so "Google One" stays as it is.
    private static final Set<String> PROCESSORS = Set.of(
            "SQ", "SUMUP", "NYX", "GOOGLE", "PAYPAL", "ZTL", "IZ", "SP", "PP");

    private static final Set<String> COMPANY_SUFFIXES = Set.of("LIMITED", "LTD", "PLC", "INC", "LLC", "UK");

    // "INT'L 0055962090" at the start of foreign card payments
    private static final Pattern FOREIGN_PREFIX = Pattern.compile("^INT'L \\S+\\s*");
    // "JPY 590 @214.5454 Visa Rate" or "USD 6.00 @ 1.3513 Visa Rate"
    private static final Pattern EXCHANGE_RATE =
            Pattern.compile("\\b[A-Z]{3} [\\d,.]+ ?@ ?[\\d.]+ Visa Rate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?\\d{6,}$");

    public void clean(Transaction transaction) {
        transaction.setCleanMerchant(cleanMerchant(transaction.getMerchant()));

        String rest = remainder(transaction.getMerchant(), transaction.getDescription());
        String paymentType = transaction.getPaymentType();
        if (paymentType != null && CARD_PAYMENT_TYPES.contains(paymentType)) {
            transaction.setLocation(location(rest));
            transaction.setReference(null);
        } else {
            transaction.setLocation(null);
            transaction.setReference(rest);
        }
    }

    static String cleanMerchant(String merchant) {
        if (merchant == null) {
            return null;
        }
        // "McDonald's" and "MCDONALDS" are the same place
        String text = merchant.toUpperCase(Locale.ROOT).replace("'", "").strip();

        // "SQ *ORICHA" -> "ORICHA"
        int star = text.indexOf('*');
        if (star > 0 && PROCESSORS.contains(text.substring(0, star).strip())) {
            text = text.substring(star + 1);
        }
        // "PRIMARK - COVENTRY" -> "PRIMARK": shops put the branch after a dash
        int dash = text.indexOf(" - ");
        if (dash > 0) {
            text = text.substring(0, dash);
        }

        List<String> words = new ArrayList<>(Arrays.stream(text.replace('*', ' ').split("\\s+"))
                .filter(word -> !word.isEmpty() && !isReferenceCode(word))
                .toList());
        while (words.size() > 1 && COMPANY_SUFFIXES.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        if (words.isEmpty()) {
            // everything looked like a reference code; a rough name beats no name,
            // because merchant memory can't remember a merchant without one
            String fallback = merchant.toUpperCase(Locale.ROOT).replaceAll("\s+", " ").strip();
            return fallback.isEmpty() ? null : fallback;
        }
        return String.join(" ", words);
    }

    // Reference codes and store numbers: "T17VY", "MD3E32VY5", "2325".
    // Short words with a digit are kept, so "O2" survives, and so are web and email
    // addresses like "WWW2.WARWICK@AC.UK".
    private static boolean isReferenceCode(String word) {
        if (word.contains(".") || word.contains("@")) {
            return false;
        }
        boolean hasDigit = word.chars().anyMatch(Character::isDigit);
        boolean allDigits = word.chars().allMatch(Character::isDigit);
        return allDigits || (hasDigit && word.length() >= 4);
    }

    // The description is the merchant line followed by the other detail lines;
    // this returns those other lines, minus foreign-payment noise.
    private static String remainder(String merchant, String description) {
        if (description == null) {
            return null;
        }
        String rest = FOREIGN_PREFIX.matcher(description.strip()).replaceFirst("");
        if (merchant != null && rest.startsWith(merchant.strip())) {
            rest = rest.substring(merchant.strip().length());
        }
        rest = EXCHANGE_RATE.matcher(rest).replaceAll("").replaceAll("\\s+", " ").strip();
        return rest.isEmpty() ? null : rest;
    }

    // "COVENTRY 4" -> "COVENTRY"; trainline's "+443332022222" is a phone number, not a place
    private static String location(String rest) {
        if (rest == null || PHONE_NUMBER.matcher(rest).matches()) {
            return null;
        }
        String place = Arrays.stream(rest.split(" "))
                .filter(word -> !word.chars().allMatch(Character::isDigit))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        return place.isEmpty() ? null : place;
    }
}
