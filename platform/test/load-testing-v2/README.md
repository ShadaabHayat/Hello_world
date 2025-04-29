# Kafka Load Testing v2

This project contains k6 scripts for load testing the ingestion engine. It supports producing user, resource device, and network device data simultaneously in common kafka topics while generating performance reports. The framework supports both Avro and JSON schema formats.

## Project Structure

```
load-testing-v2/
├── scripts/
│   ├── kafka_avro_producer.js   # Avro schema producer implementation
│   ├── kafka_json_producer.js   # JSON schema producer implementation
│   ├── config.js                # Shared configuration
│   ├── main.js                  # Main test script with producer logic
│   └── schemas/                 # Schema definitions
│       ├── resource_device_schema.js
│       ├── user_schema.js
│       └── network_device_schema.js
├── docker-compose.yml           # Docker environment setup
├── Dockerfile                   # k6 with Kafka extension
└── run-all.sh                   # Test execution script
```

## Prerequisites

1. Docker and Docker Compose installed
2. Access to Kafka broker and Schema Registry

## Configuration

### Build the docker image

```bash
docker-compose build

for linux

docker build --platform linux/amd64 -t 438465127823.dkr.ecr.us-east-1.amazonaws.com/aicore-ingestion-engine/k6-load-testing:v7 .
```

This will:

- Build the k6 container with Kafka extensions
- Mount local scripts and reports directories
- Run the tests with configured environment variables
- Generate performance reports in the `reports` directory

### Environment Variables

Configure the test using environment variables in `docker-compose.yml`:

```yaml
environment:
  KAFKA_BROKERS: "kafka1:29092"
  SCHEMA_REGISTRY_URL: "http://schema-registry:8081"
  RESOURCE_DEVICE_TOPIC_NAME: "aicore-resource-device"
  USER_TOPIC_NAME: "aicore-users-topic"
  NETWORK_DEVICE_TOPIC_NAME: "aicore-network-device"
  PRODUCE_RESOURCE_DEVICE: "true"
  PRODUCE_USER: "true"
  PRODUCE_NETWORK_DEVICE: "true"
  K6_VUS: "100"
  K6_DURATION: "2s"
  PARTITIONS_COUNT_FOR_TOPIC: "4"
  SCHEMA_TYPE: "avro"  # Options: "avro" or "json"
```

### Run the tests

```bash
docker-compose up
```

### Standalone Execution

1. Build k6 with required extensions:

```bash
docker run --rm -it -e GOOS=darwin -u "$(id -u):$(id -g)" -v "${PWD}:/xk6" \
  grafana/xk6 build v0.45.1 \
  --with github.com/mostafa/xk6-kafka@latest \
  --with github.com/grafana/xk6-dashboard@latest
```

2. Run the test:

```bash
./k6 run scripts/main.js \
  -e KAFKA_BROKERS=localhost:9092 \
  -e SCHEMA_REGISTRY_URL=http://localhost:8081 \
  -e K6_VUS=100 \
  -e K6_DURATION=30s
```

## Test Description

The test performs the following:

1. **Schema Registration**:
   - Registers schemas for resource device, user, and network device data
   - Supports both Avro and JSON schema formats
   - Validates schema compatibility with the registry

2. **Parallel Execution**:
   - Runs multiple producers simultaneously based on enabled topics
   - Each producer has its own VU (Virtual User) configuration

3. **Data Generation**:
   - Generates realistic random data for all enabled topics
   - Supports complex nested structures (up to 3 levels of nesting)
   - Follows the defined schema structure

4. **Performance Monitoring**:
   - Generates JSON and text summaries
   - Reports are saved in the `reports` directory

## Performance Tuning

Adjust these parameters for optimal performance:

- `K6_VUS`: Increase for higher throughput
- `K6_DURATION`: Extend for longer test runs
- `PRODUCE_*` flags: Enable/disable specific topics to focus testing
- `SCHEMA_TYPE`: Choose between "avro" or "json" formats
- Producer configurations in the respective producer implementation files

## Reports

Test results are available in:

- `/reports/summary.json`: Detailed metrics in JSON format
- Console output: Text summary with color formatting

## Troubleshooting

1. **Connection Issues**:
   - Verify Kafka broker and Schema Registry URLs
   - Check network connectivity from Docker container

2. **Performance Issues**:
   - Monitor system resources
   - Adjust VU count and duration
   - Check Kafka broker configurations

## Helm Chart Deployment

The application can be deployed to Kubernetes using the provided Helm chart in the `helm` directory.

### Prerequisites

1. Kubernetes cluster configured
2. Helm 3.x installed
3. Access to the container registry

### Installing the Chart

1. Review and modify the values in `helm/values.yaml` if needed:

```yaml
config:
  kafkaBrokers: "your-kafka-brokers"
  schemaRegistryUrl: "your-schema-registry-url"
  resourceDeviceTopicName: "aicore-resource-device"
  userTopicName: "aicore-users-topic"
  networkDeviceTopicName: "aicore-network-device"
  produceResourceDevice: "true"
  produceUser: "true"
  produceNetworkDevice: "true"
  k6Vus: "100"
  k6Duration: "2s"
  partitionsCountForTopic: "4"
  schemaType: "avro"  # Options: "avro" or "json"
```

2. Install the chart:

```bash
helm install k6-load-test ./helm
```

3. To override values during installation:

```bash
helm install k6-load-test ./helm \
  --set config.k6Vus=200 \
  --set config.k6Duration=5s
```

### Monitoring the Tests

1. Check pod status:

```bash
kubectl get pods -l app=k6-load-test
```

2. View test logs:

```bash
kubectl logs -f deployment/k6-load-test
```

3. Access test reports:

```bash
kubectl cp <pod-name>:/reports ./k6-reports
```

### Uninstalling the Chart

```bash
helm uninstall k6-load-test
```

## Contributing

1. Follow the existing code structure
2. Update schemas in the `schemas` directory
3. Update main.js to produce message with the new schema/data
4. Test changes using both Avro and JSON schema types
5. Test with different combinations of topic production flags
6. Test changes using both Docker Compose and standalone modes
