package com.personal_finance.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal_finance.backend.model.Category;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void moneyInCategoriesOnlyFitMoneyIn() {
        assertTrue(Category.INCOME.fitsDirection(true));
        assertFalse(Category.INCOME.fitsDirection(false));
        assertFalse(Category.MONEY_FROM_FRIENDS.fitsDirection(false));
    }

    @Test
    void paymentsToPeopleAndOwnAccountsOnlyFitMoneyOut() {
        assertTrue(Category.FRIENDS_AND_FAMILY.fitsDirection(false));
        assertFalse(Category.FRIENDS_AND_FAMILY.fitsDirection(true));
        assertTrue(Category.TRANSFERS_OUT.fitsDirection(false));
        assertFalse(Category.TRANSFERS_OUT.fitsDirection(true));
    }

    @Test
    void spendingCategoriesFitRefundsToo() {
        assertTrue(Category.TRANSPORT.fitsDirection(false));
        assertTrue(Category.TRANSPORT.fitsDirection(true));
    }
}
