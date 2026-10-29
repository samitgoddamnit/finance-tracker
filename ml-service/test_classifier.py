"""Run with:  .venv/Scripts/python -m unittest"""

import unittest

from classifier import CategoryClassifier, TransactionText, to_text


def card(merchant, amount, payment_type="VIS"):
    # card payments have a location rather than a reference, and the model doesn't see it
    return TransactionText(merchant, None, payment_type, amount)


TRAINING = [
    (card("TESCO STORES", -8.10, ")))"), "GROCERIES"),
    (card("TESCO STORES", -12.40, ")))"), "GROCERIES"),
    (card("SAINSBURYS S/MKTS", -2.04, ")))"), "GROCERIES"),
    (card("CO-OP GROUP", -3.30, ")))"), "GROCERIES"),
    (card("JD WETHERSPOON", -4.15), "EATING_OUT"),
    (card("GREGGS", -3.25, ")))"), "EATING_OUT"),
    (card("MCDONALDS", -6.79, ")))"), "EATING_OUT"),
    (card("STAGECOACH SERVICE", -3.00, ")))"), "TRANSPORT"),
    (card("TRAINLINE", -45.95), "TRANSPORT"),
    (card("TFL TRAVEL CH", -6.00, ")))"), "TRANSPORT"),
    (TransactionText("SAM CARTER", "hello chat", "BP", -21.50), "FRIENDS_AND_FAMILY"),
    (TransactionText("PRIYA SHAH", "for shopping", "BP", -50.00), "FRIENDS_AND_FAMILY"),
    (TransactionText("JORDAN ELLIS", "payment 1/12", "BP", -4.00), "FRIENDS_AND_FAMILY"),
    (TransactionText("TOM BECKER", "mcm", "CR", 38.00), "MONEY_FROM_FRIENDS"),
    (TransactionText("ALEX MORGAN", "SPOTIFY", "CR", 3.00), "MONEY_FROM_FRIENDS"),
]


class ToTextTest(unittest.TestCase):

    def test_drops_instalment_numbers_and_adds_payment_details(self):
        text = to_text(TransactionText("JORDAN ELLIS", "payment 1/12", "BP", -4.00))
        self.assertEqual("JORDAN ELLIS payment paytype_BP money_out", text)

    def test_names_contactless_payments(self):
        text = to_text(TransactionText("TESCO STORES", None, ")))", -8.10))
        self.assertEqual("TESCO STORES paytype_CONTACTLESS money_out", text)

    def test_keeps_short_merchant_names_with_digits(self):
        text = to_text(TransactionText("O2 PREPAY TOP-UP", None, "VIS", -15.00))
        self.assertTrue(text.startswith("O2 PREPAY TOP-UP"))

    def test_marks_money_in(self):
        text = to_text(TransactionText("TOM BECKER", "mcm", "CR", 38.0))
        self.assertTrue(text.endswith("paytype_CR money_in"))


class CategoryClassifierTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.model = CategoryClassifier.train(TRAINING)

    def predict(self, transaction):
        [(category, confidence)] = self.model.predict([transaction])
        return category, confidence

    def test_recognises_a_known_shop(self):
        category, _ = self.predict(card("TESCO STORES", -15.00, ")))"))
        self.assertEqual("GROCERIES", category)

    def test_learns_that_bill_payments_out_to_people_are_friends_and_family(self):
        # a name it has never seen; the "BP" + money out pattern is what gives it away
        category, _ = self.predict(TransactionText("SAM OKAFOR", "rent share", "BP", -120.00))
        self.assertEqual("FRIENDS_AND_FAMILY", category)

    def test_learns_that_credits_from_people_are_money_from_friends(self):
        category, _ = self.predict(TransactionText("CHRIS NOVAK", "spotify", "CR", 3.67))
        self.assertEqual("MONEY_FROM_FRIENDS", category)

    def test_confidence_is_a_probability(self):
        _, confidence = self.predict(card("TESCO STORES", -8.10, ")))"))
        self.assertGreater(confidence, 0.5)
        self.assertLessEqual(confidence, 1.0)

    def test_needs_two_categories_to_train(self):
        only_groceries = [example for example in TRAINING if example[1] == "GROCERIES"]
        self.assertIsNone(CategoryClassifier.train(only_groceries))

    def test_predicting_nothing_returns_nothing(self):
        self.assertEqual([], self.model.predict([]))


if __name__ == "__main__":
    unittest.main()
