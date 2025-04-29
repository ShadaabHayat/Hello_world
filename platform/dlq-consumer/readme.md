# DLQ Consumer Service

A custom Kafka Dead Letter Queue (DLQ) consumer that processes failed messages, sends email alerts, and exposes Prometheus metrics.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
  - [Environment Variables](#environment-variables)
  - [Configuration Files](#configuration-files)
- [Running the Service](#running-the-service)
  - [Using Docker Compose](#using-docker-compose)
  - [Running Locally](#running-locally)
- [Metrics](#metrics)
- [Email Notifications](#email-notifications)
- [Testing](#testing)
- [License](#license)

## Features

- Consume messages from one or more Kafka DLQ topics.
- Parse error metadata from Kafka record headers.
- Send HTML-formatted email alerts via SMTP or MX relay.
- Expose Prometheus metrics:
  - `dlq_error_messages_total` with labels `topic`, `provider`, `connector`, `exception_class`, `transform_class`.
  - `dlq_message_processing_latency_seconds` histogram with `topic` label.
  - `dlq_emails_sent_total` counter.
- Pluggable ownership mapping for routing alerts to different teams/environments.

## Prerequisites

- Access to a Kafka cluster.
- SMTP server or MX relay for sending emails.
- Prometheus server for metrics scraping.

## Project Structure

```
dlq-consumer/
├── Dockerfile
├── docker-compose.yml
├── pyproject.toml
├── poetry.lock
├── src/
│   ├── config.py
│   ├── config_loader.py
│   ├── consumer.py
│   ├── email_notifier.py
│   ├── main.py
│   ├── metrics.py
│   └── config/
│       ├── producer_owners.yaml
│       └── prometheus.yml
└── tests/
    ├── mock_ownership.yaml
    └── test_core_components.py
```

## Configuration

### Environment Variables

| Variable              | Description                                                                                   | Default                              |
|-----------------------|-----------------------------------------------------------------------------------------------|--------------------------------------|
| `KAFKA_BROKERS`       | Comma-separated Kafka bootstrap servers                                                      | `localhost:9092`                     |
| `CONSUMER_GROUP_ID`   | Kafka consumer group ID (required)                                                            | (none)                               |
| `TOPIC_NAMES`         | Comma-separated DLQ topic names (e.g., `aicore.dlq,other.dlq`)                                | (none)                               |
| `POLL_TIMEOUT_MS`     | Poll timeout in milliseconds                                                                  | `1000`                               |
| `PROMETHEUS_PORT`     | Port to expose Prometheus metrics                                                             | `8080`                               |
| `OWNERSHIP_MAP_PATH`  | Path to YAML ownership mapping                                                                | `/app/src/config/producer_owners.yaml` |
| `EMAIL_SMTP_SERVER`   | SMTP server hostname                                                                          | (none)                               |
| `EMAIL_SMTP_PORT`     | SMTP server port                                                                              | (none)                               |
| `EMAIL_FROM`          | Sender email address                                                                          | (none)                               |
| `EMAIL_TO`            | Default recipient email address (used if no mapping found)                                     | (none)                               |

### Configuration Files

- **`src/config/producer_owners.yaml`**: Maps DLQ topics → environments → regions → email lists.
- **`src/config/prometheus.yml`**: Prometheus scrape configuration for the service.

Example `producer_owners.yaml`:
```yaml
producers:
  - topic_prefix: "uz_"
    source_name: "uz"
    dev_env_email: "aabubakar@extremenetworks.com"
    staging_env_email: "aabubakar@extremenetworks.com"
    prod_env_email: "aabubakar@extremenetworks.com"
  - topic_prefix: "intel_"
    source_name: "intel"
    dev_env_email: "aabubakar@extremenetworks.com"
    staging_env_email: "aabubakar@extremenetworks.com"
    prod_env_email: "aabubakar@extremenetworks.com"
  - topic_prefix: ""
    source_name: "xiq"
    dev_env_email: "aabubakar@extremenetworks.com"
    staging_env_email: "aabubakar@extremenetworks.com"
    prod_env_email: "aabubakar@extremenetworks.com"

platform_team_email:
  dev_env_email: "aabubakar@extremenetworks.com"
  staging_env_email: "aabubakar@extremenetworks.com"
  prod_env_email: "aabubakar@extremenetworks.com"
```

## Running the Service

### Using Docker Compose

1. Clone the repository.
2. Make sure `pyproject.toml` exists.
3. poetry install --with dev
4. Start the service:
   ```bash
   docker compose up --build


## Metrics

The service exposes metrics at `http://<HOST>:<PROMETHEUS_PORT>/metrics`:

- **`dlq_error_messages_total{topic,provider,connector,exception_class,transform_class}`**: Total DLQ error messages.
- **`dlq_message_processing_latency_seconds{topic}`**: Processing latency histogram.
- **`dlq_emails_sent_total`**: Total email notifications sent.

## Email Notifications

`send_email_notification()` constructs an HTML email containing:

- Topic, provider, connector, exception details, stack trace.
- Timestamp and context information.

It retrieves recipient addresses from the ownership map, falling back to `EMAIL_TO` if none found.

## Testing

Unit tests in `tests/` cover core functionality and metrics registration.

Run tests with coverage:
```bash
poetry run pytest --cov=src
```

