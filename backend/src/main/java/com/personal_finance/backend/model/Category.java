package com.personal_finance.backend.model;

// The spending categories a transaction can be put in.
// The names are stored in the database and used as labels by the ML service,
// so renaming one means updating existing rows too. Adding one needs nothing else:
// the ML service learns it once transactions are labelled with it.
public enum Category {
    GROCERIES("Groceries"),
    EATING_OUT("Eating out"),
    TRANSPORT("Transport"),
    SUBSCRIPTIONS("Subscriptions"),
    SHOPPING("Shopping"),
    PHONE_AND_BILLS("Phone & bills"),
    RENT("Rent"),
    HEALTH_AND_FITNESS("Health & fitness"),
    ENTERTAINMENT("Entertainment"),
    // money sent to people
    FRIENDS_AND_FAMILY("Friends & family"),
    // money moved to your own accounts: savings, another bank, a credit card
    TRANSFERS_OUT("Transfers out"),
    INCOME("Income"),
    MONEY_FROM_FRIENDS("Money from friends"),
    FEES_AND_CHARGES("Fees & charges"),
    UNCATEGORISED("Uncategorised");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // money coming in, so it isn't spending
    public boolean isMoneyIn() {
        return this == INCOME || this == MONEY_FROM_FRIENDS;
    }

    // Whether this category can describe money moving in this direction. Income can't
    // go out, and payments to people or your own accounts can't come in (money back from
    // a friend is MONEY_FROM_FRIENDS). Spending categories fit both ways, because refunds
    // come back in under the category they refund.
    public boolean fitsDirection(boolean moneyIn) {
        if (isMoneyIn()) {
            return moneyIn;
        }
        if (this == FRIENDS_AND_FAMILY || this == TRANSFERS_OUT) {
            return !moneyIn;
        }
        return true;
    }
}
