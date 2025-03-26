
from kafka import KafkaProducer
from kafka.admin import KafkaAdminClient, NewTopic

import avro.schema
from avro.io import DatumWriter, BinaryEncoder

import requests

import time
from io import BytesIO
import json

import params

topic_avro_schema = {
  "type": "record",
  "name": "Value",
  "namespace": "aicore.public." + params.sink_config["table"],
    "fields": [
        {
            "name": "id",
            "type": "int",
            "default": "null"
        },
        {
            "name": "num1",
            "type": ["int", "null"],
            "default": "null"
        },
        {
            "name": "str1",
            "type": ["string", "null"],
            "default": "null"
        },
        {
            "name": "ssn",
            "type": ["string", "null"],
            "default": "null"
        },
        {
            "name": "nest1",
            "type": [
                {
                    "type": "record",
                    "name": "dummy",
                    "fields": [
                        {
                            "name": "str2",
                            "type": "string",
                            "default": "null"
                        },
                        {
                            "name": "str3",
                            "type": "string",
                            "default": "null"
                        },
                        {
                            "name": "arr1",
                            "type": {
                                "type": "array",
                                "items": "int"
                            },
                            "default": "null"
                        }
                    ]
                }, "null"]
            ,
            "default": "null"
        },
        {
            "name": "__op",
            "type": [
                "null",
                "string"
            ],
            "default": "null"
        },
    {
      "name": "__ts_ms",
      "type": [
        "null",
        "long"
      ],
      "default": "null"
    },
    {
      "name": "__deleted",
      "type": [
        "null",
        "string"
      ],
      "default": "false"
    }
    ]
}

topic_json_schema = {
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "group": "aicore.public." + params.sink_config["table"],
  "title": "Value",
  "properties": {
    "id": {
        "type": "integer"
    },
    "num1": {
        "type": "integer"
    },
    "str1": {
        "type": "string"
    },
    "ssn": {
        "type": "string"
    },
    "nest1": {
        "type": "object",
        "properties": {
            "str2": {
                "type": "string"
            },
            "str3": {
                "type": "string"
            },
            "arr1": {
                "type": "array",
                "items": {
                    "type": "integer"
                }
            }
        }
    }
  }
}

topic_avro_schema_str = json.dumps(topic_avro_schema)
topic_json_schema_str = json.dumps(topic_json_schema)

topic_avro_schema = avro.schema.parse(topic_avro_schema_str)

kafka_record_value_magic_prefix = None

operation_map = {
    "INSERT": "c",
    "UPDATE": "u",
    "DELETE": "d"
}

def generate_magic_prefix(schema_id: int) -> None:
    global kafka_record_value_magic_prefix
    bytes_writer = BytesIO()

    # Generate initial 5 magic bytes of kafka record value
    bytes_writer.write((0).to_bytes(1, byteorder='big'))
    bytes_writer.write((schema_id).to_bytes(4, byteorder='big'))
    kafka_record_value_magic_prefix = bytes_writer.getvalue()


def publish_schema(schema_format: str="AVRO") -> None:
    schema_name = f"""aicore.public.{params.sink_config["table"]}-value"""
    url = f"http://{params.schema_registry_endpoint}/subjects/{schema_name}/versions"

    schema_format = schema_format.strip().upper()
    if schema_format == "AVRO":
        topic_schema_str = topic_avro_schema_str
    elif schema_format == "JSON":
        topic_schema_str = topic_json_schema_str
    else:
        print(f"Unknown schema format: <{schema_format}>")
        exit(-2)

    payload = { "schema": topic_schema_str, "schemaType": schema_format }

    resp = requests.post(url, data=json.dumps(payload))
    print(f"{resp.status_code} | Publishing <{schema_format}> schema for <{schema_name}>")

    if resp.status_code == 200:
        schema_id = resp.json()['id']
        generate_magic_prefix(schema_id)
    else:
        generate_magic_prefix(0)
        print(resp.json())


def dict_to_avro(record: dict) -> bytes:
    datum_writer = DatumWriter(topic_avro_schema)
    bytes_writer = BytesIO()
    encoder = BinaryEncoder(bytes_writer)
    datum_writer.write(record, encoder)
    return bytes_writer.getvalue()


def push_record(producer, topic, record: dict, value_format="AVRO") -> None:
    global kafka_record_value_magic_prefix

    key = "Struct{id=%d}" % (record["id"])
    key = key.encode()

    op = record["__op"]
    record["__deleted"] = str(op == "d").strip().lower()
    record["__ts_ms"]   = int((time.time() * 1000))

    value_format = value_format.strip().upper()

    if value_format == "AVRO":
        value = dict_to_avro(record)

    elif value_format == "JSON":
        value = json.dumps(record).encode()

    else:
        print(f"Unknown Kafka record value format <{value_format}>")

    producer.send(topic, key=key, value=kafka_record_value_magic_prefix + value)


def create_topic(topic: str) -> None:
    admin_client = KafkaAdminClient(
        bootstrap_servers=params.kafka_boostrap_clusters, 
        client_id=None
    )

    topic_list = []
    topic_list.append(NewTopic(name=topic, num_partitions=1, replication_factor=1))
    try:
        admin_client.create_topics(new_topics=topic_list, validate_only=False)
    except kafka.errors.TopicAlreadyExistsError as e:
        return

    # Wait for topic to be created
    time.sleep(3)

    # Make list_topics API request to shadow confirm topic exists
    admin_client.list_topics()
    admin_client.close()


def push_records(records: list[dict], value_format="AVRO", should_create_topic: bool=True, should_publish_schema: bool=True) -> None:
    topic = "aicore.public." + params.sink_config["table"]
    producer = KafkaProducer(bootstrap_servers=params.kafka_boostrap_clusters, retries=5)

    if should_create_topic:
        print(f"Creating topic <{topic}>")
        create_topic(topic)

    if should_publish_schema:
        publish_schema(value_format)
    else:
        generate_magic_prefix(0)

    for rec in records:
        push_record(producer, topic, rec, value_format)

    producer.flush()


def generate_record_for_id(record_id: int, op: str) -> dict:
    if op.upper() == "DELETE":
        return { "id": record_id, "__op": operation_map[op] }
    else:
        return {
            "id": record_id,
            "num1": 1000 + record_id,
            "str1": "abc-" + str(record_id),
            "ssn": "very-secure-ssn-"+str(record_id),
            "nest1": {
                "str2": "xyz-" + str(record_id),
                "str3": "pqr-" + str(record_id),
                "arr1": list(range(record_id))
            },
            "__op": operation_map[op]
        }
