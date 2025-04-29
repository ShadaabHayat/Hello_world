import os

class Config:
    # Kafka
    POLL_TIMEOUT_MS = int(os.getenv("POLL_TIMEOUT_MS", "1000"))
    KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092")
    CONSUMER_GROUP_ID = os.getenv("CONSUMER_GROUP_ID")
    _TOPIC_NAMES = os.getenv("TOPIC_NAMES")
    TOPIC_NAMES = _TOPIC_NAMES.split(",") if _TOPIC_NAMES else []
    MAX_POLL_RECORDS = int(os.getenv("MAX_POLL_RECORDS", "100"))

    # Prometheus
    PROMETHEUS_PORT = int(os.getenv("PROMETHEUS_PORT", "8080"))

    # Email / MX relay
    EMAIL_SMTP_SERVER = os.getenv("EMAIL_SMTP_SERVER")
    EMAIL_SMTP_PORT = int(os.getenv("EMAIL_SMTP_PORT", "0"))
    EMAIL_FROM = os.getenv("EMAIL_FROM")
    
    RDC = os.getenv("RDC")
    ENV = os.getenv("ENV")
