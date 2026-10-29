package com.personal_finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.TransactionCleaner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransactionCleanerTest {

    private final TransactionCleaner cleaner = new TransactionCleaner();

    private Transaction clean(String merchant, String description, String paymentType) {
        Transaction transaction = new Transaction();
        transaction.setMerchant(merchant);
        transaction.setDescription(description);
        transaction.setPaymentType(paymentType);
        cleaner.clean(transaction);
        return transaction;
    }

    // merchants as they appear on real HSBC statements
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Spotify P45CD4F0E5  | SPOTIFY",
        "Spotify Limited     | SPOTIFY",
        "AMAZON* MD3E32VY5   | AMAZON",
        "AMAZON UK* N37LS23  | AMAZON",
        "Amazon Prime*M24KU  | AMAZON PRIME",
        "GOOGLE *Google One  | GOOGLE ONE",
        "Google One          | GOOGLE ONE",
        "SQ *ORICHA          | ORICHA",
        "SumUp *MENU-HUB L   | MENU-HUB L",
        "NYX*BrandedVending  | BRANDEDVENDING",
        "UBER *EATS          | UBER EATS",
        "TESCO STORES 2325   | TESCO STORES",
        "CO-OP GROUP 171641  | CO-OP GROUP",
        "771K00B2X DWP UC    | DWP UC",
        "GREGGS PLC          | GREGGS",
        "PIXIV INC           | PIXIV",
        "FINERY WARWICK LTD  | FINERY WARWICK",
        "PRIMARK - COVENTRY  | PRIMARK",
        "NISA - MILLENNIUM   | NISA",
        "O2 PREPAY TOP-UP    | O2 PREPAY TOP-UP",
        "McDonald's          | MCDONALDS",
        "MCDONALDS           | MCDONALDS",
        "WWW2.WARWICK@AC.UK  | WWW2.WARWICK@AC.UK",
    })
    void cleansMerchantNames(String merchant, String expected) {
        assertEquals(expected, clean(merchant, merchant, "VIS").getCleanMerchant());
    }

    @Test
    void cardPaymentsGetALocation() {
        Transaction transaction = clean("TESCO STORES 2325", "TESCO STORES 2325 COVENTRY 4", ")))");

        assertEquals("COVENTRY", transaction.getLocation());
        assertNull(transaction.getReference());
    }

    @Test
    void foreignPaymentsLoseTheExchangeRateNoise() {
        Transaction transaction = clean(
                "PIXIV INC", "INT'L 0055962090 PIXIV INC TOKYO JPY 590 @214.5454 Visa Rate", "VIS");

        assertEquals("PIXIV", transaction.getCleanMerchant());
        assertEquals("TOKYO", transaction.getLocation());
    }

    @Test
    void phoneNumbersAreNotLocations() {
        assertNull(clean("trainline", "trainline +443332022222", "VIS").getLocation());
    }

    @Test
    void otherPaymentsGetAReference() {
        Transaction transaction = clean("Sam Carter", "Sam Carter hello chat", "BP");

        assertEquals("SAM CARTER", transaction.getCleanMerchant());
        assertEquals("hello chat", transaction.getReference());
        assertNull(transaction.getLocation());
    }

    @Test
    void descriptionThatDoesntRepeatTheMerchantIsTheReference() {
        Transaction transaction = clean("Tesco", "Weekly groceries", null);

        assertEquals("TESCO", transaction.getCleanMerchant());
        assertEquals("Weekly groceries", transaction.getReference());
    }

    @Test
    void merchantMadeOnlyOfReferenceCodesKeepsItsRawName() {
        assertEquals("0055962090", clean("0055962090", "0055962090", "VIS").getCleanMerchant());
    }
}
