import logging
import time
import traceback
from kafka import KafkaConsumer, TopicPartition, OffsetAndMetadata
from .config import Config
from .metrics import DLQ_ERRORS, DLQ_PROCESSING_LATENCY
from .email_notifier import send_email_notification
from .config_loader import load_ownership_map

logger = logging.getLogger(__name__)

class DlqCustomConsumer:
    def __init__(self):
        try:
            self.ownership_map = load_ownership_map()
            self.topics = Config.TOPIC_NAMES
            self.consumer = KafkaConsumer(
                bootstrap_servers=Config.KAFKA_BROKERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                group_id=Config.CONSUMER_GROUP_ID,
                max_poll_records=Config.MAX_POLL_RECORDS,
            )
            self.consumer.subscribe(self.topics)
            logger.info(f"Consumer initialized for topics: {self.topics}")
        except Exception as e:
            logger.error(f"Failed to initialize consumer: {e}")
            logger.debug(traceback.format_exc())
            raise

    def run(self):
        logger.info("Starting DLQ consumer loop")
        while True:
            try:
                records = self.consumer.poll(timeout_ms=Config.POLL_TIMEOUT_MS)
                for tp, msgs in records.items():
                    for msg in msgs:
                        self._process_with_metrics(msg)
                        self._commit_partition_offset(tp, msg.offset)

            except Exception as e:
                logger.error(f"Polling error: {e}")
                logger.debug(traceback.format_exc())
                self.consumer.close()
    
    def _commit_partition_offset(self, tp: TopicPartition, offset: int):
        """
        Commit offset+1 for this TopicPartition only.
        """
        self.consumer.commit({
            tp: OffsetAndMetadata(offset + 1, None)
        })

    def _process_with_metrics(self, msg):
        start_time = time.time()
        try:
            extracted_info = self._process_message(msg)
            DLQ_ERRORS.labels(
                topic=extracted_info['topic'],
                provider="unknown",  # hardcoded as provider was not part of headers, will be updated later
                connector=extracted_info['connector'],
                exception_class=extracted_info['exception_class'],
                transform_class=extracted_info['transform_class']
            ).inc()
        except Exception as e:
            logger.error(f"Error processing message: {e}")
            logger.debug(traceback.format_exc())
        finally:
            elapsed = time.time() - start_time
            topic = msg.topic or "unknown"
            DLQ_PROCESSING_LATENCY.labels(topic=topic).observe(elapsed)
            logger.info(f"Processed message in {elapsed:.2f} seconds")

    def _process_message(self, msg):
        try:
            logger.info(f"Consumed message: topic={msg.topic}, partition={msg.partition}, offset={msg.offset}")
            logger.info(f"Message headers: {msg.headers}")
            
            header_dict = {}
            for k, v in msg.headers or []:
                try:
                    header_dict[k] = v.decode("utf-8") if isinstance(v, bytes) else str(v)
                except Exception as e:
                    logger.warning(f"Could not decode header {k}: {e}")

            logger.info(f"Header dict: {header_dict}")

            topic = header_dict.get("__connect.errors.topic", msg.topic)
            emails = self._get_emails(topic, Config.ENV)

            extracted_info = {
                "topic": topic,
                "rdc": Config.RDC,
                "env": Config.ENV,
                "provider": "unknown",
                "connector": header_dict.get("__connect.errors.connector.name", "unknown"),
                "exception_class": header_dict.get("__connect.errors.exception.class.name", "N/A"),
                "transform_class": header_dict.get("__connect.errors.class.name", "N/A"),
                "exception_message": header_dict.get("__connect.errors.exception.message", "N/A"),
                "stacktrace": header_dict.get("__connect.errors.exception.stacktrace", "")[:1000],
                "emails": emails
            }


            send_email_notification(
                subject=f"DLQ Alert - {extracted_info['topic']}",
                body=extracted_info["exception_message"],
                payload=extracted_info,
                email_recipients=emails  # Pass resolved emails
            )

            return extracted_info

        except Exception as e:
            logger.error(f"Error during message processing: {e}")
            logger.debug(traceback.format_exc())
            raise


    def _get_emails(self, topic: str, env: str):
        try:

            producer_owners = self.ownership_map.get("producers", [])
            platform_team_emails = self.ownership_map.get("platform_team_email", {})
            
            producer_emails = []

            # Get the platform team email for the current environment
            platform_team_email = platform_team_emails.get(f"{env.lower()}_env_email", "")
            
            # Check for a matching producer owner
            for owner in producer_owners:
                if owner["topic_prefix"] and topic.startswith(owner["topic_prefix"]):  # Match only non-empty topic_prefix
                    email_key = f"{env.lower()}_env_email"
                    producer_emails = [owner.get(email_key, platform_team_email)]
                    logger.info(f"Matched topic '{topic}' with producer '{owner['source_name']}' and email_key '{email_key}'")
                    break

            # If no producer owner is found, explicitly match xiq (empty topic_prefix)
            if not producer_emails:
                for owner in producer_owners:
                    if owner["topic_prefix"] == "":  # Explicitly match xiq
                        email_key = f"{env.lower()}_env_email"
                        producer_emails = [owner.get(email_key, platform_team_email)]
                        logger.info(f"No specific match found for topic '{topic}'. Using xiq as fallback.")
                        break

            # If still no producer emails are found, fallback to platform team only
            if not producer_emails:
                logger.warning(f"No matching producer owner found for topic={topic}. Notifying platform team only.")
                producer_emails = []

            # Combine producer emails with the platform team email
            return producer_emails + [platform_team_email]
        except Exception as e:
            logger.warning(f"Failed to resolve emails for topic={topic}, env={env}, rdc={rdc}: {e}")
            return [platform_team_email]