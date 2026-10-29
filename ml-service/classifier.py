"""Guesses a transaction's category from its bank text.

The model is TF-IDF features fed into logistic regression:
  - word features pick up whole words ("TESCO", "COFFEE", "BUS")
  - character n-gram features (3-5 letters) cope with the way bank statements
    chop names off ("Kitty Cafe Birming") and with spelling variants
  - extra tokens for the payment type and whether money came in or went out,
    so it can learn things like "bill payments out to a person are transfers"

Logistic regression gives a probability for every category, which the backend
uses as a confidence score: low-confidence guesses stay Uncategorised.
"""

from dataclasses import dataclass

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline, make_union

UNCATEGORISED = "UNCATEGORISED"

# HSBC payment type codes that aren't plain words
PAYMENT_TYPE_NAMES = {")))": "CONTACTLESS"}


@dataclass
class TransactionText:
    # the backend's cleaned fields (see TransactionCleaner.java), not the raw bank text:
    # merchant without reference codes or processors, and the payment's note if it has one.
    # The location is left out on purpose, so "COVENTRY" can't come to mean "groceries".
    merchant: str | None
    reference: str | None
    payment_type: str | None
    amount: float | None


def to_text(transaction: TransactionText) -> str:
    """Flattens a transaction into the text the model reads.

    Words containing digits are dropped from the reference ("payment 1/12"):
    they're instalment or reference numbers that would only add noise.
    """
    # the merchant is already cleaned by the backend, which keeps short ones like "O2"
    words = (transaction.merchant or "").split()
    words += [word for word in (transaction.reference or "").split()
              if not any(char.isdigit() for char in word)]

    if transaction.payment_type:
        payment_type = PAYMENT_TYPE_NAMES.get(transaction.payment_type, transaction.payment_type)
        words.append(f"paytype_{payment_type}")
    words.append("money_in" if (transaction.amount or 0) > 0 else "money_out")
    return " ".join(words)


def _build_pipeline():
    return make_pipeline(
        make_union(
            TfidfVectorizer(analyzer="word", lowercase=True, token_pattern=r"[^\s]+"),
            TfidfVectorizer(analyzer="char_wb", lowercase=True, ngram_range=(3, 5)),
        ),
        # higher C = trusts the training data more; with few examples per category the
        # default (1.0) spreads probability too thinly and nothing clears the threshold
        LogisticRegression(C=10, max_iter=2000, class_weight="balanced"),
    )


class CategoryClassifier:
    def __init__(self, pipeline):
        self._pipeline = pipeline

    @classmethod
    def train(cls, examples, weights=None):
        """Trains on (TransactionText, category) pairs.

        Returns None when there's nothing worth learning yet: logistic regression
        needs at least two different categories to tell apart.
        """
        texts = [to_text(transaction) for transaction, _ in examples]
        labels = [category for _, category in examples]
        if len(set(labels)) < 2:
            return None

        pipeline = _build_pipeline()
        pipeline.fit(texts, labels, logisticregression__sample_weight=weights)
        return cls(pipeline)

    @property
    def categories(self):
        return list(self._pipeline.classes_)

    def predict(self, transactions):
        """Returns a (category, confidence) pair for each transaction."""
        if not transactions:
            return []
        probabilities = self._pipeline.predict_proba([to_text(t) for t in transactions])
        classes = self._pipeline.classes_
        results = []
        for row in probabilities:
            best = row.argmax()
            results.append((str(classes[best]), float(row[best])))
        return results
