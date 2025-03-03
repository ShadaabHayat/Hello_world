# Data Synchronization Test

This repository contains a Python-based test suite to validate data synchronization from PostgreSQL to Kafka and S3. The test validates that data inserted into PostgreSQL is correctly consumed by Kafka and subsequently stored in S3 as Parquet files. The tests include checks for compression and encryption of Parquet files stored in S3.

## Prerequisites

To run the tests, ensure the following services are set up and running:

- **PostgreSQL** - Used as the source database for data insertion.
- **Kafka** - The message broker for data consumption.
- **MinIO / AWS S3** - Object storage for storing Parquet files.
- **Schema Registry** - To fetch Avro schemas for Kafka messages.

### Required Python Libraries

You can install the required Python libraries using the `requirements.txt` file.


## Required Services
Before running the tests, ensure you have the following services set up:
### PostgreSQL

Run a local or remote PostgreSQL instance.

### Kafka

**•** Set up a Kafka cluster with at least one topic.

**•** The topic should be configured to match KAFKA_TOPIC in the configuration (e.g., aicore.public.users).

### MinIO or AWS S3

**•** If using MinIO, set it up to mimic AWS S3 locally.

**•** If using AWS S3, ensure that you have access keys and a valid S3 bucket created.

**•** Set the bucket name in S3_BUCKET in the configuration   

### Schema Registry

**•** Ensure you have a running instance of Schema Registry that provides Avro schemas for Kafka messages




## Configuration

The configuration is controlled through environment variables. You can configure these variables using an .env file or set them directly in your environment.

The following environment variables are required:


| Variable              | Description                                                | Default Value                                     |
|-----------------------|------------------------------------------------------------|--------------------------------------------------|
| `KAFKA_BROKERS`        | Comma-separated list of Kafka brokers                      | `localhost:9092,localhost:9093,localhost:9094`    |
| `KAFKA_GROUP_ID`       | Kafka consumer group ID                                    | `my-consumer-group`                              |
| `KAFKA_TOPIC`          | Kafka topic to consume messages from                       | `aicore.public.users`                            |
| `PG_HOST`              | PostgreSQL database host                                    | `localhost`                                      |
| `PG_PORT`              | PostgreSQL database port                                    | `5433`                                           |
| `PG_DB`                | PostgreSQL database name                                    | `aicore`                                         |
| `PG_USER`              | PostgreSQL database username                                | `postgres`                                       |
| `PG_PASSWORD`          | PostgreSQL database password                                | `1234`                                           |
| `S3_BUCKET`            | S3 Bucket name                                             | `my-bucket`                                      |
| `S3_PREFIX`            | S3 Prefix for file storage                                 | `warehouse/aicore.public.users/`                |
| `S3_ENDPOINT`          | MinIO S3-compatible endpoint (if using MinIO)              | `http://localhost:9000`                          |
| `S3_ACCESS_KEY`        | S3 Access Key                                              | ``                          |
| `S3_SECRET_KEY`        | S3 Secret Key                                              | ``      |
| `SCHEMA_REGISTRY_URL`  | Schema registry URL                                        | `http://localhost:8081`                          |




## Running the Tests


### Set Up Your Environment:

**•** Ensure all required services (PostgreSQL, Kafka, S3/MinIO, Schema Registry) are running and accessible.

**•** Configure the environment variables by creating a .env file in the root directory (as shown above).

### Run Tests with pytest:

To run the tests, use pytest from the command line:

<pre>  pytest -v </pre>

This will run all the tests in the module. You can also specify a particular test file or test case to run.


<pre>  pytest test_dataflow.py  -s --log-cli-level=INFO </pre>


## Test Cases

The test suite contains the following test cases:

### test_postgres_to_kafka: 
Verifies that data inserted into PostgreSQL is consumed correctly from Kafka.
### test_postgres_to_s3:
 Verifies that data inserted into PostgreSQL is written correctly as a Parquet file in S3.
### test_compression: 
Verifies that Parquet files stored in S3 are compressed using a valid method.
### test_encryption:
 Verifies that Parquet files stored in S3 are encrypted




 We hope this guide helps you set up and run the tests in your environment. If you have any questions or run into issues, feel free to reach out


 





