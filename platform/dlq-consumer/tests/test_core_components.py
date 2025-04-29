import os
from unittest.mock import MagicMock
import pytest
from prometheus_client import CollectorRegistry, generate_latest


@pytest.fixture(autouse=True)
def patch_load_ownership(monkeypatch):
    import src.consumer as c_mod
    monkeypatch.setattr(c_mod, 'load_ownership_map', lambda: {
        "producers": [
            {"topic_prefix": "uz_", "source_name": "uz", "dev_env_email": "alerts-uz-dev@extremenetworks.com"},
            {"topic_prefix": "intel_", "source_name": "intel", "dev_env_email": "alerts-intel-dev@extremenetworks.com"},
            {"topic_prefix": "", "source_name": "xiq", "dev_env_email": "alerts-xiq-dev@extremenetworks.com"}
        ],
        "platform_team_email": {
            "dev_env_email": "platform-team-dev@extremenetworks.com"
        }
    })
    monkeypatch.setenv("ENV", "dev")
    monkeypatch.setenv("RDC", "local")
    monkeypatch.setenv("KAFKA_BROKERS", "dummy:1234")  # Mocked for KafkaConsumer

    import importlib
    from src import config
    importlib.reload(config)
    return


def test_config_loading(monkeypatch):
    monkeypatch.setenv("KAFKA_BROKERS", "localhost:9092")
    monkeypatch.setenv("CONSUMER_GROUP_ID", "dlq-group")
    monkeypatch.setenv("TOPIC_NAMES", "dlq-topic1,dlq-topic2")
    monkeypatch.setenv("PROMETHEUS_PORT", "9090")
    monkeypatch.setenv("EMAIL_SMTP_SERVER", "smtp.example.com")
    monkeypatch.setenv("EMAIL_SMTP_PORT", "25")
    monkeypatch.setenv("EMAIL_FROM", "alerts@example.com")
    monkeypatch.setenv("EMAIL_TO", "team@example.com")

    import importlib
    from src import config
    importlib.reload(config)
    Config = config.Config

    assert Config.KAFKA_BROKERS == "localhost:9092"
    assert Config.CONSUMER_GROUP_ID == "dlq-group"
    assert Config.TOPIC_NAMES == ["dlq-topic1", "dlq-topic2"]
    assert Config.PROMETHEUS_PORT == 9090


def test_kafka_header_parsing():
    headers = [
        ("__connect.errors.topic", b"dlq-provider2"),
        ("__connect.errors.connector.name", b"test-connector"),
        ("__connect.errors.class.name", b"SomeTransform"),
        ("__connect.errors.exception.class.name", b"SomeException"),
    ]
    header_dict = {k: v.decode("utf-8") for k, v in headers}
    assert header_dict["__connect.errors.topic"] == "dlq-provider2"
    assert header_dict["__connect.errors.class.name"] == "SomeTransform"


def test_prometheus_metrics():
    from src.metrics import DLQ_ERRORS, DLQ_PROCESSING_LATENCY
    registry = CollectorRegistry()
    DLQ_ERRORS.labels(topic="test", provider="p", connector="c", exception_class="e", transform_class="t").inc()
    DLQ_PROCESSING_LATENCY.labels(topic="test").observe(0.5)
    output = generate_latest(registry)
    assert isinstance(output, bytes)


def test_email_counter_metric():
    from src.metrics import EMAILS_SENT
    count_before = EMAILS_SENT._value.get()
    EMAILS_SENT.inc()
    assert EMAILS_SENT._value.get() == count_before + 1


def test_get_emails_matching_producer(monkeypatch):
    import src.consumer as c_mod
    monkeypatch.setattr(c_mod, 'KafkaConsumer', MagicMock())
    consumer = c_mod.DlqCustomConsumer()
    emails = consumer._get_emails("uz_topic1", "dev")
    assert "alerts-uz-dev@extremenetworks.com" in emails
    assert "platform-team-dev@extremenetworks.com" in emails


def test_get_emails_with_fallback(monkeypatch):
    import src.consumer as c_mod
    monkeypatch.setattr(c_mod, 'KafkaConsumer', MagicMock())
    consumer = c_mod.DlqCustomConsumer()
    emails = consumer._get_emails("random_topic", "dev")
    assert "alerts-xiq-dev@extremenetworks.com" in emails
    assert "platform-team-dev@extremenetworks.com" in emails


def test_process_message(monkeypatch):
    import src.consumer as c_mod
    import importlib
    monkeypatch.setenv("ENV", "dev")
    monkeypatch.setenv("RDC", "local")
    importlib.reload(c_mod)
    monkeypatch.setattr(c_mod, 'send_email_notification', MagicMock())
    monkeypatch.setattr(c_mod, 'KafkaConsumer', MagicMock())
    consumer = c_mod.DlqCustomConsumer()

    class DummyMsg:
        topic = "fallback"
        partition = 0
        offset = 0
        headers = [
            ("__connect.errors.topic", b"test-topic"),
            ("__connect.errors.connector.name", b"conn"),
            ("__connect.errors.exception.class.name", b"Exc"),
            ("__connect.errors.class.name", b"Transform"),
            ("__connect.errors.exception.message", b"Failed"),
            ("__connect.errors.exception.stacktrace", b"Trace")
        ]

    info = consumer._process_message(DummyMsg())
    assert info["topic"] == "test-topic"
    assert info["connector"] == "conn"
    assert info["exception_class"] == "Exc"
    assert info["transform_class"] == "Transform"
    assert info["exception_message"] == "Failed"
    assert "emails" in info
    assert len(info["emails"]) > 0