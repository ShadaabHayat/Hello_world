
import requests
import boto3

import time
import sys

import params

def delete_api(instances_list: list[str], resource_name: str):
    for instance in instances_list:

        if resource_name == "connectors":
            url = f"http://{params.kafka_ui_endpoint}/api/clusters/local/connects/connect/connectors/{instance}"
        else:
            url = f"http://{params.kafka_ui_endpoint}/api/clusters/local/{resource_name}/{instance}"

        response = requests.delete(url)
        print(f"{response.status_code} | Deleting {resource_name[:-1]} <{instance}>")

        if response.ok or response.status_code == 404:
            continue
        elif response.status_code == 500 and response.json()["code"]==5000:
            continue
        else:
            print(response.status_code, file=sys.stderr)
            print(response.text, file=sys.stderr)
            print(f"Could not delete {resource_name[:-1]} <{instance}>", file=sys.stderr)
            exit(-1)

def delete_topics(topics: list[str]):
    delete_api(topics, "topics")

def delete_connectors(connectors: list[str]):
    delete_api(connectors, "connectors")

def delete_schemas(schemas: list[str]):
    delete_api(schemas, "schemas")

def delete_consumer_groups(groups: list[str]):
    delete_api(groups, "consumer-groups")

def empty_s3_bucket():
    bucket_name = params.sink_config["bucket"]
    print(f"Emptying s3 bucket <{bucket_name}>")
    s3 = boto3.resource(service_name='s3', endpoint_url='http://' + params.minio_s3_endpoint)
    bucket = s3.Bucket(bucket_name)
    keys = bucket.objects.all()
    if len(list(keys)) > 0:
        bucket.delete_objects(Delete={"Objects": [{"Key": k.key} for k in keys], "Quiet": False})

def delete_all():
    table  = params.sink_config["table"]
    sink_name = params.sink_config["name"]

    consumer_group = "connect-" + sink_name
    topic_name = "aicore.public." + table
    schema_name = topic_name + "-value"

    delete_connectors([sink_name])

    # Need time b/w connector deletion and associated consumer-group deletion
    empty_s3_bucket()
    time.sleep(3) 

    delete_schemas([schema_name])
    delete_consumer_groups([consumer_group])
    delete_topics(["aicore.dlq", topic_name])
