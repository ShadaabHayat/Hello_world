
import boto3
import polars as pl
import time

from kafka import KafkaConsumer

import params

def read_kafka(topic: str, retries: int=10) -> list[dict]:
    consumer = KafkaConsumer(topic, bootstrap_servers=params.kafka_boostrap_clusters, group_id=None)
    while retries > 0:
        try:
            resp = consumer.poll(1)
            if len(resp) > 0:
                return list(resp.values())[0]
            else:
                consumer.seek_to_beginning()
                time.sleep(1)
        # Ignore "AssertionError: No partitions are currently assigned" error
        # Retry after backoff
        except AssertionError as er:
            pass
        retries -= 1
    return None

def read_s3() -> (list[pl.DataFrame], list[str]):
    s3 = boto3.resource(service_name='s3', endpoint_url='http://' + params.minio_s3_endpoint)
    bucket_name = params.sink_config["bucket"]

    bucket = s3.Bucket(bucket_name)

    keys = []
    for summary in bucket.objects.all():
        keys.append(summary.key)

    df_list = []
    key_uris = []

    for key in keys:
        try:
            uri = "s3://" + bucket_name + "/" + key
            local_path = "/tmp/s3.data"

            bucket.download_file(key, local_path)

            df = pl.read_parquet(local_path)

            df_list.append(df)
            key_uris.append(uri)
        except Exception as e:
            print(f"Failed to read key <{key}>: {e}")

    return df_list, key_uris
