
import os

kafka_boostrap_clusters  = os.environ["BOOTSTRAP_SERVERS"]
kafka_ui_endpoint        = os.environ["KAFKA_UI_ENDPOINT"]
kafka_connect_endpoint   = os.environ["KAFKA_CONNECT_ENDPOINT"]
schema_registry_endpoint = os.environ["SCHEMA_REGISTRY_ENDPOINT"]
minio_s3_endpoint        = os.environ["MINIO_S3_ENDPOINT"]

sink_config = dict()
sink_config["name"]               = os.environ.get("SINK_CONNECTOR_NAME", "s3-sink-connector")
sink_config["table"]              = os.environ.get("TEST_TABLE_NAME", "users")
sink_config["bucket"]             = os.environ.get("SINK_BUKCET_NAME", "my-bucket")
sink_config["output.compression"] = os.environ.get("SINK_OUTPUT_COMPRESSION", "gzip")
