# src/main.py
import logging
import signal
import sys
from .consumer import DlqCustomConsumer
from .config import Config
from .metrics import start_metrics_server


logging.basicConfig(level=logging.INFO)

consumer = DlqCustomConsumer()

def main():
    start_metrics_server(Config.PROMETHEUS_PORT)
    consumer.run()

if __name__ == "__main__":
    main()
