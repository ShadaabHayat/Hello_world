
import requests

import time
import json

import params

def delete_connector(connector_name: str) -> None:
    print(f"Deleting connector <{connector_name}>")

    url = f"http://{params.kafka_connect_endpoint}/connectors/{connector_name}"

    resp = requests.delete(url)
    if not resp.ok:
        print(resp.text)


def create_connector(connector_name: str, config: dict) -> None:
    url = f"http://{params.kafka_connect_endpoint}/connectors/{connector_name}/config"

    headers = {
        "Content-Type": "application/json"
    }

    response = requests.put(url, headers=headers, data=json.dumps(config))
    print(f"{response.status_code} | Creating connector <{connector_name}>")

    if not response.ok:
        print(f"Failed to create <{connector_name}> connector")
        print(response.text)
        exit(-3)

def create_sink_connector(connector_name: str, overrides: dict=dict()):

    config = {
        "connector.class": "io.confluent.connect.s3.S3SinkConnector",
        "locale": "en-US",
        "timezone": "UTC",

        "behavior.on.null.values": "ignore",

        "errors.deadletterqueue.context.headers.enable": "true",
        "errors.deadletterqueue.topic.name": "aicore.dlq",
        "errors.deadletterqueue.topic.replication.factor": "1",
        "errors.tolerance": "all",

        "flush.size": "1",
        "format.class": "io.confluent.connect.s3.format.parquet.ParquetFormat",

        "key.converter": "org.apache.kafka.connect.storage.StringConverter",
        "key.converter.schema.registry.url": params.schema_registry_endpoint,
        "key.converter.schemas.enable": "true",

        "value.converter": "io.confluent.connect.avro.AvroConverter",
        "value.converter.schema.registry.url": "http://" + params.schema_registry_endpoint,
        "value.converter.schemas.enable": "false",

        "parquet.codec": params.sink_config["output.compression"],

        "partition.duration.ms": "3600000",
        "partition.field.format.path": "true",
        "partition.field.name": "rdc, env",
        "partitioner.class": "com.canelmas.kafka.connect.FieldAndTimeBasedPartitioner",

        "path.format": "'year'=YYYY/'month'=MM/'day'=dd",

        "rotate.interval.ms": "-1",
        "rotate.schedule.interval.ms": "300000",

        "s3.bucket.name": params.sink_config["bucket"],
        "s3.region": "eu-west-1",

        "schema.compatibility": "FORWARD",

        "storage.class": "io.confluent.connect.s3.storage.S3Storage",
        "store.url": "http://" + params.minio_s3_endpoint,

        "tasks.max": "1",

        "timestamp.extractor": "RecordField",
        "timestamp.field": "last_modified_at",

        "topics": "aicore.public." + params.sink_config["table"],
        "topics.dir": "warehouse",

        "transforms": "InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,Mask_aicore_public_users_ssn,tsFormat",

        "transforms.InsertEnvInfo.static.field": "env",
        "transforms.InsertEnvInfo.static.value": "e2e-test",
        "transforms.InsertEnvInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertRDCInfo.static.field": "rdc",
        "transforms.InsertRDCInfo.static.value": "minikube",
        "transforms.InsertRDCInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertTimeStamp.timestamp.field": "last_modified_at",
        "transforms.InsertTimeStamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.Mask_aicore_public_users_ssn.mask.fields": "ssn",
        "transforms.Mask_aicore_public_users_ssn.topic.name": "aicore.public." + params.sink_config["table"],
        "transforms.Mask_aicore_public_users_ssn.type": "org.extremenetworks.com.MaskCustomFields$Value",

        "transforms.tsFormat.field": "last_modified_at",
        "transforms.tsFormat.format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "transforms.tsFormat.target.type": "string",
        "transforms.tsFormat.type": "org.apache.kafka.connect.transforms.TimestampConverter$Value"
      }

    config.update(overrides)

    create_connector(connector_name, config)
