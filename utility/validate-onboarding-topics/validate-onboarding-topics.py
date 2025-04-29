import argparse
import csv
import json
import os

import requests


def get_all_kafka_topic_offsets(api_url):
    """
    Retrieves Kafka topic names and their maximum offsets for all pages
    from the given API URL.

    Args:
        api_url (str): The base URL of the Kafka API endpoint for topics.

    Returns:
        dict: A dictionary where keys are topic names and values are their maximum offsets.
              Returns None if an error occurs during the API request.
    """
    all_topic_offsets = {}
    page = 1
    try:
        while True:
            current_url = f"{api_url}/api/clusters/common-kafka/topics?page={page}"
            response = requests.get(current_url)
            response.raise_for_status()  # Raise an exception for bad status codes
            data = response.json()

            if "topics" in data:
                for topic_info in data["topics"]:
                    topic_name = topic_info["name"]
                    if topic_info["partitions"]:
                        offset_max = topic_info["partitions"][0]["offsetMax"]
                        all_topic_offsets[topic_name] = offset_max
                    else:
                        all_topic_offsets[topic_name] = None
            else:
                print("Warning: 'topics' key not found in the API response.")
                return all_topic_offsets if all_topic_offsets else None

            if "pageCount" in data and page < data["pageCount"]:
                page += 1
            else:
                break  # Exit the loop if no more pages

        return all_topic_offsets

    except requests.exceptions.RequestException as e:
        print(f"Error during topic API request: {e}")
        return None
    except json.JSONDecodeError as e:
        print(f"Error decoding topic API JSON response: {e}")
        return None


def get_registered_schemas(schema_registry_url):
    """
    Retrieves a list of registered schema subjects from the given Schema Registry API URL,
    handling pagination.

    Args:
        schema_registry_url (str): The base URL of the Schema Registry API endpoint for schemas.

    Returns:
        list: A list of registered schema subjects.
              Returns None if an error occurs during the API request.
    """
    all_schemas = []
    page = 1
    try:
        while True:
            current_url = (
                f"{schema_registry_url}/api/clusters/common-kafka/schemas?page={page}"
            )
            response = requests.get(current_url)
            response.raise_for_status()  # Raise an exception for bad status codes
            data = response.json()

            if "schemas" in data:
                for schema_info in data["schemas"]:
                    if "subject" in schema_info:
                        all_schemas.append(schema_info["subject"])
            else:
                print(
                    "Warning: 'schemas' key not found in the schema registry API response."
                )
                return all_schemas if all_schemas else None

            if "pageCount" in data and page < data["pageCount"]:
                page += 1
            else:
                break  # Exit the loop if no more pages

        return all_schemas

    except requests.exceptions.RequestException as e:
        print(f"Error during schema registry API request: {e}")
        return None
    except json.JSONDecodeError as e:
        print(f"Error decoding schema registry API JSON response: {e}")
        return None


def validate_kafka_topics(
    kafka_api_base_url,
    schema_registry_base_url,
    topics_file,
    output_folder="output",
    output_csv_file="onboarding-issues.csv",
):
    """
    Validates the status of a list of topics and saves the output to a CSV file
    in the specified output folder.

    Args:
        kafka_api_base_url (str): The base URL of the Kafka API (e.g., http://localhost:8080).
        schema_registry_base_url (str): The base URL of the Schema Registry API (e.g., http://localhost:8080).
        topics_file (str): The path to the file containing the list of topics (one per line).
        output_folder (str): The name of the folder where the CSV report will be saved.
                             Defaults to "output".
        output_csv_file (str): The name of the CSV file where the report will be saved.
                               Defaults to "onboarding-issues.csv".

    Returns:
        bool: True if the report was generated successfully, False otherwise.
    """
    kafka_topics_url = f"{kafka_api_base_url}/api/clusters/common-kafka/topics"
    schema_registry_url = (
        f"{schema_registry_base_url}/api/clusters/common-kafka/schemas"
    )

    try:
        with open(topics_file, "r") as f:
            target_topics = [line.strip() for line in f]
    except FileNotFoundError:
        print(f"Error: Topics file '{topics_file}' not found.")
        return False

    all_topic_offsets = get_all_kafka_topic_offsets(kafka_api_base_url)
    registered_schemas = get_registered_schemas(kafka_api_base_url)

    if all_topic_offsets is None or registered_schemas is None:
        return False

    report_data = []
    header = ["Topic Name", "Is Present", "Has Messages", "Has Schema"]
    report_data.append(header)

    for topic in target_topics:
        is_present = topic in all_topic_offsets
        has_messages = (
            is_present
            and all_topic_offsets[topic] is not None
            and all_topic_offsets[topic] > 0
        )
        has_schema = any(
            schema.startswith(f"{topic}-") or schema == topic
            for schema in registered_schemas
        )
        if not is_present or not has_messages or not has_schema:
            report_data.append([topic, is_present, has_messages, has_schema])

    # Create the output folder if it doesn't exist
    os.makedirs(output_folder, exist_ok=True)
    output_path = os.path.join(output_folder, output_csv_file)

    # print the report data to the console
    for row in report_data:
        print(", ".join(map(str, row)))

    try:
        with open(output_path, "w", newline="") as csvfile:
            writer = csv.writer(csvfile)
            writer.writerows(report_data)
        print(f"Health report saved to: {output_path}")
        return True
    except IOError:
        print(f"Error: Could not write to CSV file: {output_path}")
        return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate Kafka topic health.")
    parser.add_argument(
        "--api-base-url",
        type=str,
        default="http://localhost:8080",
        help="Base URL for Kafka and Schema Registry APIs (default: http://localhost:8080)",
    )
    args = parser.parse_args()

    kafka_api_base_url = args.api_base_url
    schema_registry_base_url = args.api_base_url
    topics_file = "config/topics.txt"
    output_folder = "output"
    output_csv_file = "onboarding-issues.csv"

    if validate_kafka_topics(
        kafka_api_base_url,
        schema_registry_base_url,
        topics_file,
        output_folder,
        output_csv_file,
    ):
        pass
    else:
        print("Failed to generate the topic health report.")
