# AICore Development Environment Setup

This directory contains the Docker Compose configuration for setting up a complete development environment for the AICore Ingestion Engine. The environment includes Kafka, Schema Registry, Kafka Connect, MinIO (S3-compatible storage), and PostgreSQL.

## Components

1. **Kafka Ecosystem**
    - **Kafka Broker** (port: 9092)
      - Single-node Kafka cluster using Kraft (no Zookeeper)
      - Internal and external listeners configured
      - Controller and broker roles combined
    - **Schema Registry** (port: 8081)
      - Manages Avro schemas
      - Integrated with Kafka for schema storage
    - **Kafka Connect** (port: 8083)
      - Custom-built image with S3 connectors
      - Avro conversion support
      - Configurable plugin path
      - Auto-restart capability
    - **Kafka UI** (port: 8080)
      - Web interface for Kafka management
      - Cluster monitoring
      - Topic and schema management
1. **Storage Services**
    - **MinIO** (ports: 9000, 9001)
      - S3-compatible object storage
      - Web console on port 9001
      - Default credentials: minioadmin/minioadmin
    - **PostgreSQL** (port: 5433)
      - Version: 15.1
      - Logical replication enabled
      - Multiple database support
      - Default credentials:
        - Username: postgres
        - Password: 1234

## Prerequisites

1. Docker and Docker Compose installed
2. At least 4GB of RAM available for containers
3. Ports 8080-8083, 9000-9001, 5433, and 9092 available

## Quick Start

1. **Create Docker Network**:

    ```bash
    docker network create my-network
    ```

1. **Start Services**:

    ```bash
    docker-compose up -d
    ```

1. **Verify Services**:

    ```bash
    docker-compose ps
    ```

## Service URLs

- Kafka UI: <http://localhost:8080>
- Schema Registry: <http://localhost:8081>
- Kafka Connect: <http://localhost:8083>
- MinIO Console: <http://localhost:9001>
- PostgreSQL: localhost:5433

## Environment Variables

### Kafka Connect

```env
CONNECT_BOOTSTRAP_SERVERS: kafka1:29092
CONNECT_VALUE_CONVERTER: io.confluent.connect.avro.AvroConverter
CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL: http://schema-registry:8081
```

### PostgreSQL

```env
POSTGRES_USER: postgres
POSTGRES_PASSWORD: 1234
POSTGRES_MULTIPLE_DATABASES: aicore
```

### MinIO

```env
AWS_ACCESS_KEY_ID: minioadmin
AWS_SECRET_ACCESS_KEY: minioadmin
```

## Kafka Connect Connectors

### Creating connectors

CURL commands can be used to instantiate connectors. Here's an example:

```bash
curl -X PUT -H "Content-Type: application/json" \
              http://localhost:8083/connectors/my-connector-name/config \
              -d @../../platform/helm/config/my-connector-config.json
```

YAML files under `platform/helm/config` can be converted to JSON and then CURL can be used to create a connector.
Note the following must be added to the configurations:

```json
"store.url": "http://minio:9000",                 # Point to MinIO as simulated S3
"s3.bucket.name": "my-bucket",                    # This and the next two configurations
"transforms.InsertEnvInfo.static.value": "test",  #   must be present, though typically
"transforms.InsertRDCInfo.static.value": "ws2r1"  #   in the xcloud-appconfig repo
```

## Data Persistence

- PostgreSQL data is persisted in the `postgres_data` volume
- MinIO data is stored in the `/data` directory within the container

## Troubleshooting

1. **Service Won't Start**:

    ```bash
    docker-compose logs [service-name]
    ```

1. **Kafka Connect Issues**:

    - Check if Schema Registry is accessible
    - Verify connector plugins are properly loaded

    ```bash
    curl -s localhost:8083/connector-plugins | jq
    ```

1. **PostgreSQL Connection Issues**:

    - Ensure port 5433 is not in use
    - Check if database initialization completed

    ```bash
    docker-compose logs postgres-test
    ```

## Maintenance

### Stopping Services

```bash
docker-compose down
```

### Cleaning Up

```bash
docker-compose down -v  # Removes volumes
docker-compose down --rmi all  # Removes images
```

### Updating Services

```bash
docker-compose pull  # Pull latest images
docker-compose up -d --build  # Rebuild and restart
```

## Security Notes

- This is a development setup with default credentials
- Not recommended for production use
- All services are exposed on localhost only
- Use proper security measures in production

## Additional Resources

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kafka Connect Documentation](https://docs.confluent.io/platform/current/connect/index.html)
- [MinIO Documentation](https://docs.min.io/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/15/index.html)
