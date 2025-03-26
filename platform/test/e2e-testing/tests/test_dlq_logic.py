
import time
import json

from kafka import KafkaProducer
import pytest

import cleanup
import kafka_connect
import params
import producer
import reader
from test_template import assertion_template

def test_dlq_for_invalid_records():
    record_count = 41
    topic = "aicore.public." + params.sink_config["table"]

    cleanup.delete_all()
    producer.create_topic(topic)
    producer.publish_schema()

    dlq_producer = KafkaProducer(bootstrap_servers=params.kafka_boostrap_clusters, retries=5)

    for i in range(record_count):
        rec = {
            "id": i,
            "str5": "qwerty-" + str(i),
            "__op": "@"
        }
        dlq_producer.send(
            topic,
            key=f"Struct(id={i})".encode(),
            value=producer.kafka_record_value_magic_prefix + json.dumps(rec).encode())

    dlq_producer.flush()

    kafka_connect.create_sink_connector(params.sink_config["name"])

    time.sleep(10)

    dlq_records = reader.read_kafka("aicore.dlq")
    assert len(dlq_records) == record_count, f"Expected to find <{record_count}> in DLQ, found <{len(dlq_records)}> records instead"
