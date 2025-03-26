
import boto3
import pandas as pd
import time

from kafka import KafkaConsumer

import os
import params

pd.set_option('display.max_rows', 100)
pd.set_option('display.max_columns', 16)
pd.set_option('display.width', 1000)

def read_kafka(topic: str, retries: int=10) -> list[dict]:
    consumer = KafkaConsumer(topic, bootstrap_servers=params.kafka_boostrap_clusters, group_id=None)
    while retries > 0:
        resp = consumer.poll(1)
        if len(resp) > 0:
            return list(resp.values())[0]
        else:
            consumer.seek_to_beginning()
            time.sleep(0.1)
        retries -= 1
    return None

def read_s3() -> (list[pd.DataFrame], list[str]):
    s3 = boto3.resource(service_name='s3', endpoint_url='http://' + params.minio_s3_endpoint)
    bucket_name = params.sink_config["bucket"]

    bucket = s3.Bucket(bucket_name)

    keys = []
    for summary in bucket.objects.all():
        keys.append(summary.key)

    s3fs_options = {
        "anon": False,
        "endpoint_url": "http://" + params.minio_s3_endpoint
    }

    df_list = []
    key_uris = []

    for key in keys:
        try:
            uri = "s3://" + bucket_name + "/" + key
            df = pd.read_parquet(uri, storage_options=s3fs_options)

            df_list.append(df)
            key_uris.append(uri)
        except Exception as e:
            print(f"Failed to read key <{key}>: {e}")

    return df_list, key_uris
