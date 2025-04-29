# from prometheus_client import Counter, Histogram, start_http_server

# MESSAGES_PROCESSED = Counter('dlq_messages_processed_total', 'Total messages processed')
# PROCESSING_LATENCY = Histogram('dlq_message_processing_seconds', 'Time spent processing messages')

# def start_metrics_server(port: int):
#     start_http_server(port)


# src/metrics.py

from prometheus_client import start_http_server, Counter, Histogram

# New counter for DLQ errors with multiple labels
DLQ_ERRORS = Counter(
    'dlq_error_messages_total',
    'Total number of DLQ messages processed with error',
    ['topic', 'provider', 'connector', 'exception_class', 'transform_class']
)

# Optional latency tracker if you want
DLQ_PROCESSING_LATENCY = Histogram(
    'dlq_message_processing_latency_seconds',
    'Time taken to process a DLQ message',
    ['topic']
)


EMAILS_SENT = Counter(
    'dlq_emails_sent_total',
    'Number of DLQ alert emails (or Teams posts) successfully sent'
)

def start_metrics_server(port: int):
    start_http_server(port)


