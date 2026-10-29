# ML categorisation service

Guesses categories for transactions that the backend's merchant matching and
keyword rules couldn't place. It talks to the backend over Kafka and reads its
training data straight from the backend's Postgres.

## How categorisation works

For each new transaction the backend tries, in order:

1. **Merchant memory**: a merchant you've categorised yourself on the website
2. **Keyword rules**: `backend/src/main/resources/merchant-rules.csv`
3. **This service**: a text classifier, answering over Kafka
4. Otherwise it stays **Uncategorised**

Before any of that, the backend's `TransactionCleaner` tidies each transaction into
`cleanMerchant` (no reference codes, card processors or "Ltd"), `location` (card
payments) and `reference` (the note on other payments). Everything here reads those
fields; the location is deliberately left out of the model's input.

The classifier trains on every transaction labelled by steps 1 and 2, with your
own choices weighted three times as heavily as rules. Each time you change a
category on the website, the backend sends a retrain message, and this service
retrains and re-predicts everything still uncategorised or ML-guessed.

Predictions below 50% confidence are ignored and left Uncategorised; change it with
`categorisation.ml-confidence-threshold` in the backend's `application.yaml`.

## Kafka topics

| Topic | From → to | Message |
|---|---|---|
| `category-requests` | backend → ML | `{"transactionId", "cleanMerchant", "reference", "paymentType", "amount"}` |
| `category-retrain` | backend → ML | `{"reason"}` |
| `category-predictions` | ML → backend | `{"transactionId", "category", "confidence"}` |

## Running it

Set up once:

```
cd ml-service
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
```

Then, with Docker and the backend running:

```
.venv\Scripts\python service.py
```

It trains on startup and logs how many transactions it learned from.
It reads the database credentials from `backend/.env` (copy `backend/.env.example`
to create it). `DATABASE_URL` and `KAFKA_BOOTSTRAP_SERVERS` override the defaults.

## Tests

```
.venv\Scripts\python -m unittest
```
