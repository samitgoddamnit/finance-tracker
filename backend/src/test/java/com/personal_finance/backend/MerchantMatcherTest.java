package com.personal_finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.MerchantMatcher;
import com.personal_finance.backend.service.TransactionCleaner;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantMatcherTest {

    private final MerchantMatcher matcher = new MerchantMatcher();
    private static final TransactionCleaner cleaner = new TransactionCleaner();

    // cleaned the same way the app does before matching
    private static Transaction transaction(String merchant, String description, String amount, String paymentType) {
        Transaction transaction = new Transaction();
        transaction.setMerchant(merchant);
        transaction.setDescription(description);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setPaymentType(paymentType);
        cleaner.clean(transaction);
        return transaction;
    }

    private static Transaction transaction(String merchant, String description, String amount) {
        return transaction(merchant, description, amount, null);
    }

    // merchants as they appear on real HSBC statements
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "SAINSBURYS S/MKTS     | SAINSBURYS S/MKTS CAMBRIDGE                 | -2.04  | GROCERIES",
        "TESCO STORES 2325     | TESCO STORES 2325 COVENTRY 4                | -8.10  | GROCERIES",
        "CO-OP GROUP 171641    | CO-OP GROUP 171641 LEEDS                    | -3.30  | GROCERIES",
        "JD WETHERSPOON        | JD WETHERSPOON Watford                      | -4.15  | EATING_OUT",
        "UBER *EATS            | UBER *EATS HELP.UBER.COM                    | -35.73 | EATING_OUT",
        "Kitty Cafe Birming    | Kitty Cafe Birming Birmingham               | -9.00  | EATING_OUT",
        "NYX*BrandedVending    | NYX*BrandedVending Bournemouth              | -1.50  | EATING_OUT",
        "TFL TRAVEL CH         | TFL TRAVEL CH TFL.GOV.UK/CP                 | -6.00  | TRANSPORT",
        "FLIX                  | FLIX Bristol                                | 11.49  | TRANSPORT",
        "Amazon Prime*M24KU    | Amazon Prime*M24KU amzn.co.uk/pm            | -8.99  | SUBSCRIPTIONS",
        "AMZNMktplace*T17VY    | AMZNMktplace*T17VY amazon.co.uk             | -4.99  | SHOPPING",
        "Spotify P45CD4F0E5    | Spotify P45CD4F0E5 London                   | -21.99 | SUBSCRIPTIONS",
        "CLAUDE.AI SUBSCRIP    | INT'L 0095748949 CLAUDE.AI SUBSCRIP ANTHROPIC.COM | -18.00 | SUBSCRIPTIONS",
        "O2 PREPAY TOP-UP      | O2 PREPAY TOP-UP READING                    | -15.00 | PHONE_AND_BILLS",
        "LEEDS GYM             | LEEDS GYM LEEDS                             | -38.00 | HEALTH_AND_FITNESS",
        "STEAMGAMES.COM 425    | INT'L 0083947240 STEAMGAMES.COM 425 Hamburg | -11.28 | ENTERTAINMENT",
        "Non-Sterling          | Non-Sterling Transaction Fee                | -0.07  | FEES_AND_CHARGES",
        "771K00B2X DWP UC      | 771K00B2X DWP UC                            | 412.20 | INCOME",
        "UNI BURSARY PAYMT     | UNI BURSARY PAYMT                           | 300.00 | INCOME",
    })
    void matchesKnownMerchants(String merchant, String description, String amount, Category expected) {
        assertEquals(Optional.of(expected), matcher.matchRule(transaction(merchant, description, amount)));
    }

    @Test
    void friendPayingBackForSpotifyIsNotASubscription() {
        assertEquals(Optional.empty(), matcher.matchRule(transaction("ALEX MORGAN", "ALEX MORGAN SPOTIFY", "3.00")));
    }

    @Test
    void keywordsMustStartAWord() {
        // "GYM" shouldn't match inside another word, and "O2" shouldn't match "BO2X"
        assertEquals(Optional.empty(), matcher.matchRule(transaction("ENERGYMAX", "ENERGYMAX LONDON", "-3.00")));
        assertEquals(Optional.empty(), matcher.matchRule(transaction("BO2X LTD", "BO2X LTD", "-3.00")));
    }

    @Test
    void rentIsFoundInThePaymentReference() {
        assertEquals(Optional.of(Category.RENT), matcher.matchRule(
                transaction("DANIEL REEVES", "DANIEL REEVES Rent payment 4", "-1950.00", "BP")));
    }

    @Test
    void locationsDontTriggerRules() {
        // "COSTA" is a coffee chain rule, but here it's just where the shop is
        assertEquals(Optional.empty(), matcher.matchRule(
                transaction("SURF HIRE LTD", "SURF HIRE LTD COSTA DEL SOL", "-30.00", "VIS")));
    }

    @Test
    void unknownMerchantsAreLeftForTheMl() {
        assertEquals(Optional.empty(), matcher.matchRule(transaction("Sam Carter", "Sam Carter hello chat", "-21.50")));
        assertEquals(Optional.empty(), matcher.matchRule(transaction("WARWICK UNIVERSITY", "WARWICK UNIVERSITY COVENTRY", "-6.20")));
    }

    @Test
    void merchantKeyIgnoresReferenceCodes() {
        assertEquals(
                matcher.merchantKey(transaction("AMAZON* MD3E32VY5", "", "-4.99")),
                matcher.merchantKey(transaction("AMAZON* NO4Y719Q4", "", "-14.99")));
        assertEquals(Optional.of("AMAZON|OUT"), matcher.merchantKey(transaction("AMAZON* MD3E32VY5", "", "-4.99")));
        assertEquals(
                matcher.merchantKey(transaction("Spotify Limited", "", "-21.99")),
                matcher.merchantKey(transaction("Spotify P45CD4F0E5", "", "-21.99")));
    }

    @Test
    void merchantKeySeparatesMoneyInAndOut() {
        assertEquals(Optional.of("TOM BECKER|IN"), matcher.merchantKey(transaction("Tom Becker", "", "38.00")));
        assertEquals(Optional.of("TOM BECKER|OUT"), matcher.merchantKey(transaction("Tom Becker", "", "-10.00")));
    }
}
