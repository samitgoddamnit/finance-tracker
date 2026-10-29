"""The ML categorisation service.

Listens on Kafka for:
  category-requests  a new transaction nothing else could place -> predict it
  category-retrain   the labelled data changed -> retrain, then re-predict
                     everything still waiting
and answers on category-predictions with {"transactionId", "category", "confidence"}.
The backend decides whether a prediction is confident enough to use.

Run with:  .venv/Scripts/python service.py
"""

import json
import logging
import os

from kafka import KafkaConsumer, KafkaProducer

import store
from classifier import CategoryClassifier, TransactionText

KAFKA_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
REQUESTS_TOPIC = "category-requests"
RETRAIN_TOPIC = "category-retrain"
PREDICTIONS_TOPIC = "category-predictions"

log = logging.getLogger("ml-service")


def retrain():
    examples, weights = store.load_training_data()
    model = CategoryClassifier.train(examples, weights)
    if model is None:
        log.info("Not enough labelled transactions to train on yet (%d)", len(examples))
    else:
        log.info("Trained on %d labelled transactions across %d categories",
                 len(examples), len(model.categories))
    return model


def send_predictions(producer, model, transactions):
    """transactions is a list of (transaction id, TransactionText)."""
    if model is None or not transactions:
        return
    predictions = model.predict([text for _, text in transactions])
    for (transaction_id, _), (category, confidence) in zip(transactions, predictions):
        prediction = {
            "transactionId": transaction_id,
            "category": category,
            "confidence": round(confidence, 4),
        }
        producer.send(PREDICTIONS_TOPIC, json.dumps(prediction).encode("utf-8"))
    producer.flush()
    log.info("Sent %d predictions", len(transactions))


def retrain_and_predict_pending(producer):
    model = retrain()
    send_predictions(producer, model, store.load_pending())
    return model


def request_to_transaction(request):
    return request["transactionId"], TransactionText(
        merchant=request.get("cleanMerchant"),
        reference=request.get("reference"),
        payment_type=request.get("paymentType"),
        amount=request.get("amount"),
    )


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    # kafka-python logs every connection at INFO
    logging.getLogger("kafka").setLevel(logging.WARNING)

    producer = KafkaProducer(bootstrap_servers=KAFKA_SERVERS)
    consumer = KafkaConsumer(
        REQUESTS_TOPIC,
        RETRAIN_TOPIC,
        bootstrap_servers=KAFKA_SERVERS,
        group_id="ml-service",
        # Kafka remembers where this group got up to. The very first time there's nothing
        # remembered, so start from the beginning: with "latest", messages sent while the
        # consumer is still joining the group get skipped. Replaying old ones is harmless.
        auto_offset_reset="earliest",
    )

    model = retrain_and_predict_pending(producer)
    log.info("Listening for transactions on %s", KAFKA_SERVERS)

    while True:
        batch = consumer.poll(timeout_ms=1000)
        messages = [message for records in batch.values() for message in records]
        if not messages:
            continue

        if any(message.topic == RETRAIN_TOPIC for message in messages):
            # one retrain covers the whole batch: requests in it are already
            # saved as pending, so they get predicted with the new model
            model = retrain_and_predict_pending(producer)
            continue

        if model is None:
            # nothing to predict with yet; these stay Uncategorised until a retrain
            continue
        requests = [request_to_transaction(json.loads(message.value)) for message in messages]
        send_predictions(producer, model, requests)


if __name__ == "__main__":
    main()
