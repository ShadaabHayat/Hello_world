import os
import json
import pytest
import requests
import psycopg2
import boto3
import pyarrow.parquet as pq
from io import BytesIO
from datetime import datetime
from confluent_kafka import Consumer, KafkaException, KafkaError,TopicPartition
from avro.io import DatumReader, BinaryDecoder
import avro.schema
import avro.io
import warnings


class Config:
    SCHEMA_REGISTRY_URL = os.getenv('SCHEMA_REGISTRY_URL', "http://localhost:8081")
    KAFKA_BROKERS = os.getenv('KAFKA_BROKERS', "localhost:9092,localhost:9093,localhost:9094")
    KAFKA_GROUP_ID = os.getenv('KAFKA_GROUP_ID', "my-consumer-group")
    KAFKA_TOPIC = os.getenv('KAFKA_TOPIC', "aicore.public.users")
    
    PG_HOST = os.getenv('PG_HOST', 'localhost')
    PG_PORT = os.getenv('PG_PORT', '5433')
    PG_DB = os.getenv('PG_DB', 'aicore')
    PG_USER = os.getenv('PG_USER', 'postgres')
    PG_PASSWORD = os.getenv('PG_PASSWORD', '1234')
    
    S3_BUCKET = os.getenv('S3_BUCKET', 'my-bucket')
    S3_PREFIX = f'warehouse/{KAFKA_TOPIC}/'
    S3_ENDPOINT = os.getenv('S3_ENDPOINT', "http://localhost:9000")
    S3_ACCESS_KEY = os.getenv('S3_ACCESS_KEY', "kAKtOMFLwwcO7HOeMySf")
    S3_SECRET_KEY = os.getenv('S3_SECRET_KEY', "WrrOyPgpGBZZfBehY0ME5cgt6tWBPplza40ccrHU")

class DataSynchronizer:
    @staticmethod
    def insert_data_into_postgres():
        """
        Insert sample data into PostgreSQL
        """
        try:
            with psycopg2.connect(
                dbname=Config.PG_DB, 
                user=Config.PG_USER, 
                password=Config.PG_PASSWORD, 
                host=Config.PG_HOST, 
                port=Config.PG_PORT
            ) as conn:
                with conn.cursor() as cursor:
                    cursor.execute(
                        "INSERT INTO public.users (user_name, user_email, user_status) "
                        "VALUES ('Test one', 'test.one@example.com', 'active')"
                    )
                conn.commit()
                return {
                    'user_name': 'Test one',
                    'user_email': 'test.one@example.com',
                    'user_status': 'active'
                }
        except Exception as e:
            print(f"PostgreSQL Insertion Error: {e}")
            raise

    @staticmethod
    def decode_debezium_message(raw_bytes: bytes, avro_schema: dict) -> dict:
        """
        Decode a Debezium-formatted Avro message
        """
        try:
            if len(raw_bytes) < 5:
                raise ValueError("Message too short to contain Debezium headers")
            
            actual_message = raw_bytes[5:]
            schema = avro.schema.parse(json.dumps(avro_schema))
            
            decoder = BinaryDecoder(BytesIO(actual_message))
            datum_reader = DatumReader(schema)
            
            return datum_reader.read(decoder)
        except Exception as e:
            print(f"Message Decoding Error: {e}")
            raise

    @staticmethod
    def fetch_schema_from_registry(subject: str):
        """
        Fetch the latest schema for the given subject from the schema registry
        """
        schema_url = f"{Config.SCHEMA_REGISTRY_URL}/subjects/{subject}/versions/latest"
        response = requests.get(schema_url)

        if response.status_code == 200:
            schema = response.json()
            return json.loads(schema['schema'])
        else:
            print(f"Schema Fetch Failed: {response.status_code}")
            return None

    @staticmethod
    def consume_message():
        """
        Consume and deserialize a single message from Kafka, ensuring we get the latest message.
        """
        consumer_config = {
            'bootstrap.servers': Config.KAFKA_BROKERS,
            'group.id': Config.KAFKA_GROUP_ID,
            'auto.offset.reset': 'latest',
            'enable.auto.commit': False,
            'max.poll.interval.ms': 300000
        }
        
        consumer = Consumer(consumer_config)
        consumer.subscribe([Config.KAFKA_TOPIC])
        
        try:
            # Seek to end of each partition to get latest offsets
            assignments = consumer.assignment()
            if not assignments:
                # Wait for assignment
                while not assignments:
                    consumer.poll(timeout=1.0)
                    assignments = consumer.assignment()
            
            # Seek to end for each partition
            for partition in assignments:
                # Get the end offset for this partition
                _, high_watermark = consumer.get_watermark_offsets(partition)
                if high_watermark > 0:  # If there are messages
                    # Create TopicPartition with the desired offset
                    tp = TopicPartition(partition.topic, partition.partition, high_watermark - 1)
                    consumer.seek(tp)
            
            # Now poll for the message
            msg = consumer.poll(timeout=30.0)
            
            if msg is None:
                print("No message received.")
                return []
                
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    print(f"End of partition reached {msg.partition}")
                else:
                    raise KafkaException(msg.error())
                    
            raw_value = msg.value()
            if not raw_value:
                print("Empty message received.")
                return []
                
            # Extract schema details from message headers
            headers = msg.headers() or []
            schema_name = next((v.decode('utf-8') for k, v in headers if k == b'__schema'), 'public')
            db_name = next((v.decode('utf-8') for k, v in headers if k == b'__db'), 'aicore')
            
            full_topic = f"{db_name}.{schema_name}.users"
            schema_dict = DataSynchronizer.fetch_schema_from_registry(f"{full_topic}-value")
            
            if not schema_dict:
                print("Schema fetch failed.")
                return []
                
            # Decode the message using the fetched schema
            decoded_message = DataSynchronizer.decode_debezium_message(raw_value, schema_dict)
            
            # Commit the message offset manually
            consumer.commit()
            return [decoded_message]
            
        except Exception as e:
            print(f"Kafka Consumption Error: {e}")
            raise
        finally:
            consumer.close()

    @staticmethod
    def generate_s3_key(kafka_message: dict) -> str:
        """
        Generate S3 key based on last_modified_at timestamp
        """
        last_modified_at = kafka_message.get('last_modified_at', '')
        if not last_modified_at:
            return None

        try:
            last_modified_at = last_modified_at.replace('Z', '+00:00')
            timestamp = datetime.strptime(last_modified_at, "%Y-%m-%dT%H:%M:%S.%f%z")
            

           
            return (f"warehouse/aicore.public.users/"
                    f"last_modified_at={timestamp.isoformat().replace('+00:00', '')}Z/"
                    f"year={timestamp.year}/"
                    f"month={timestamp.month:02d}/"
                    f"day={timestamp.day:02d}")
        except Exception as e:
            print(f"S3 Key Generation Error: {e}")
            return None

    @staticmethod
    def read_parquet_from_s3(bucket_name: str, prefix: str):
        """
        Read the latest Parquet file from S3
        """
        s3_client = boto3.client(
            's3',
            endpoint_url=Config.S3_ENDPOINT,
            aws_access_key_id=Config.S3_ACCESS_KEY,
            aws_secret_access_key=Config.S3_SECRET_KEY,
            config=boto3.session.Config(signature_version='s3v4')
        )

        try:
            response = s3_client.list_objects_v2(Bucket=bucket_name, Prefix=prefix)
            parquet_files = [obj['Key'] for obj in response.get('Contents', []) if obj['Key'].endswith('.parquet')]
            if not parquet_files:
                print("No Parquet files found.")
                return None, None, None

            latest_file = max(parquet_files)
            obj = s3_client.get_object(Bucket=bucket_name, Key=latest_file)
            
            parquet_data = obj['Body'].read()
            table = pq.read_table(BytesIO(parquet_data))
            parquet_object=BytesIO(parquet_data)
            data = table.to_pandas().iloc[0].to_dict()

            return data, latest_file, table,parquet_object,s3_client
        except Exception as e:
            print(f"S3 Parquet Reading Error: {e}")
            return None, None, None,None


    @staticmethod
    def check_compression(parquet_file):

        try:
            # Ensure that the input is a valid ParquetFile object
            if not isinstance(parquet_file, pq.ParquetFile):
                raise TypeError(f"Expected a pyarrow.ParquetFile, got {type(parquet_file)}")

            # Access metadata from the Parquet file
            metadata = parquet_file.metadata

            # Loop through row groups to check compression
            for i in range(metadata.num_row_groups):
                # Get the compression method for the first column of each row group
                compression_method = metadata.row_group(i).column(0).compression

                if compression_method is None:
                    print(f"Compression method not specified in row group {i}.")
                    return False

                # Check if the compression method matches one of the expected values
                compression_method = compression_method.upper()  # Convert to uppercase for uniformity
                
                if compression_method not in ['SNAPPY', 'GZIP']:
                    print(f"Unexpected compression method in row group {i}: {compression_method}")
                    return False
                
                print(f"Compression used in row group {i}: {compression_method}")

            return True

        except Exception as e:
            print(f"Compression check error: {e}")
            return False
        
    @staticmethod
    def check_s3_encryption(s3_client, bucket_name, file_key):
   
        try:
            response = s3_client.head_object(Bucket=bucket_name, Key=file_key)
            encryption_type = response.get('ServerSideEncryption')
            
            if encryption_type:
                return encryption_type
            else:
                return 'No Encryption applied'
    
        except Exception as e:
            return f"Error: {e}"



@pytest.fixture(scope="module")
def pg_data():
    """
    Fixture to insert data into PostgreSQL and return the inserted data.
    This fixture will run only once per module.
    """
    pg_data = DataSynchronizer.insert_data_into_postgres()
    yield pg_data


@pytest.fixture(scope="module")
def kafka_data():
    """
    Fixture to consume message from Kafka and return the consumed data.
    This fixture will run only once per module.
    """
    messages = DataSynchronizer.consume_message()
    s3_prefix = DataSynchronizer.generate_s3_key(messages[0])
    s3_data, file_key, table,parquet_object,s3_client = DataSynchronizer.read_parquet_from_s3(Config.S3_BUCKET, s3_prefix)

    # Yield the values as a tuple
    yield messages, s3_prefix, s3_data, file_key, table,parquet_object,s3_client

# Test cases
def test_postgres_to_kafka(pg_data, kafka_data):
    """
    Test data synchronization from PostgreSQL to Kafka
    """
    # Unpack the values returned from the fixture
    messages, s3_prefix, s3_data, file_key, table,parquet_object,s3_client = kafka_data
    
    # Assertions
    assert len(messages) == 1, f"Expected 1 message, got {len(messages)}"
    message = messages[0]
    
    # Validate Kafka message data matches PostgreSQL insert
    assert message['user_name'] == pg_data['user_name'], \
        f"Mismatch in user_name. PostgreSQL: {pg_data['user_name']}, Kafka: {message['user_name']}"
    assert message['user_email'] == pg_data['user_email'], \
        f"Mismatch in user_email. PostgreSQL: {pg_data['user_email']}, Kafka: {message['user_email']}"
    assert message['user_status'] == pg_data['user_status'], \
        f"Mismatch in user_status. PostgreSQL: {pg_data['user_status']}, Kafka: {message['user_status']}"

def test_postgres_to_s3(pg_data, kafka_data):
    """
    Test data synchronization from PostgreSQL to S3
    """
    # Unpack the values returned from the fixture
    messages, s3_prefix, s3_data, file_key, table ,parquet_object,s3_client= kafka_data
    
    assert len(messages) == 1, f"Expected 1 message, got {len(messages)}"
    
    # Assertions for S3
    assert s3_prefix is not None, "Failed to generate S3 key"
    assert s3_data is not None, "Failed to read data from S3"
    
    # Validate S3 data matches PostgreSQL insert
    assert s3_data['user_name'] == pg_data['user_name'], \
        f"Mismatch in user_name. PostgreSQL: {pg_data['user_name']}, S3: {s3_data['user_name']}"
    assert s3_data['user_email'] == pg_data['user_email'], \
        f"Mismatch in user_email. PostgreSQL: {pg_data['user_email']}, S3: {s3_data['user_email']}"
    assert s3_data['user_status'] == pg_data['user_status'], \
        f"Mismatch in user_status. PostgreSQL: {pg_data['user_status']}, S3: {s3_data['user_status']}"

  
def test_compression(kafka_data):

    messages, s3_prefix, s3_data, file_key, table ,parquet_object,s3_client=kafka_data
    compression_result = DataSynchronizer.check_compression(pq.ParquetFile(parquet_object))
    assert compression_result, "Parquet file compression check failed"


def test_encryption(kafka_data):
    messages, s3_prefix, s3_data, file_key, table, parquet_object, s3_client = kafka_data

    # Call the helper function to check encryption
    encryption_result = DataSynchronizer.check_s3_encryption(s3_client, Config.S3_BUCKET, file_key)
    print(f"Encryption result: {encryption_result}")  # Debugging line

    # Fail the test if no encryption is applied
    assert encryption_result != 'No Encryption applied', "Test failed: No encryption applied to the object."
    assert encryption_result in ['AES256', 'aws:kms'], f"Unexpected encryption type: {encryption_result}"
if __name__ == "__main__":
    pytest.main([__file__])

