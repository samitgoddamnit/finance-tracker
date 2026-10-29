"""Reads training data and transactions waiting for a prediction from the backend's Postgres."""

import os
from pathlib import Path

import psycopg

from classifier import UNCATEGORISED, TransactionText

# the backend's .env holds the database credentials, so both services share one copy
BACKEND_ENV = Path(__file__).resolve().parent.parent / "backend" / ".env"


def _read_env_file(path):
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    return values


def _database_url():
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    settings = {**_read_env_file(BACKEND_ENV), **os.environ}
    try:
        user, password = settings["POSTGRES_USER"], settings["POSTGRES_PASSWORD"]
    except KeyError:
        raise SystemExit(f"No database credentials: copy backend/.env.example to {BACKEND_ENV}")
    database = settings.get("POSTGRES_DB", "transactions")
    return f"postgresql://{user}:{password}@localhost:5432/{database}"


DATABASE_URL = _database_url()

# Labels the backend is sure of. Your own choices count for more than keyword rules,
# so they win when the two disagree.
SOURCE_WEIGHTS = {"USER": 3.0, "MERCHANT": 3.0, "RULE": 1.0}

TRAINING_SQL = """
    select clean_merchant, reference, payment_type, amount, category, category_source
    from transactions
    where category_source in ('USER', 'MERCHANT', 'RULE')
      and category is not null
      and category <> %s
"""

# NONE = nothing placed it yet, ML = an older guess that a retrained model might improve
PENDING_SQL = """
    select transactionid, clean_merchant, reference, payment_type, amount
    from transactions
    where category_source in ('NONE', 'ML')
"""


def load_training_data():
    """Returns (examples, weights): examples are (TransactionText, category) pairs."""
    with psycopg.connect(DATABASE_URL) as connection:
        rows = connection.execute(TRAINING_SQL, (UNCATEGORISED,)).fetchall()
    examples = [
        (TransactionText(merchant, reference, payment_type, _to_float(amount)), category)
        for merchant, reference, payment_type, amount, category, _ in rows
    ]
    weights = [SOURCE_WEIGHTS[source] for *_, source in rows]
    return examples, weights


def load_pending():
    """Returns (transaction id, TransactionText) for everything the ML service should (re)predict."""
    with psycopg.connect(DATABASE_URL) as connection:
        rows = connection.execute(PENDING_SQL).fetchall()
    return [
        (transaction_id, TransactionText(merchant, reference, payment_type, _to_float(amount)))
        for transaction_id, merchant, reference, payment_type, amount in rows
    ]


def _to_float(amount):
    return float(amount) if amount is not None else None
