# Kafka Load Testing v2

This project contains k6 scripts for load testing the ingestion engine. It supports producing user and device data simultaneously in common kafka topics while generating performance reports.

## Project Structure

```
load-testing-v2/
├── scripts/
│   ├── kafka_producer.js    # Base Kafka producer implementation
│   ├── main.js              # Main test script with producer logic
│   └── schemas/            # Avro schemas
│       ├── resource_device_schema.js
│       └── user_schema.js
|       |.................
├── docker-compose.yml      # Docker environment setup
├── Dockerfile             # k6 with Kafka extension
└── run-all.sh            # Test execution script
```

## Prerequisites

1. Docker and Docker Compose installed
2. Access to Kafka broker and Schema Registry

## Configuration

### Build the docker image

```bash
docker-compose build

for linux

docker build --platform linux/amd64 -t jawadahmadd/k6-load-testing:v1 .
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
  K6_VUS: "100"
  K6_DURATION: "2s"
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
   - Registers Avro schemas for both user and device data
   - Validates schema compatibility

2. **Parallel Execution**:
   - Runs user and device producers simultaneously
   - Each producer has its own VU (Virtual User) configuration

3. **Data Generation**:
   - Generates realistic random data for both users and devices
   - Follows the defined Avro schema structure

4. **Performance Monitoring**:
   - Generates JSON and text summaries
   - Reports are saved in the `reports` directory

## Performance Tuning

Adjust these parameters for optimal performance:

- `K6_VUS`: Increase for higher throughput
- `K6_DURATION`: Extend for longer test runs
- Producer configurations in `kafka_producer.js`

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
  k6Vus: "100"
  k6Duration: "2s"
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
3. Update main.js to produce message with the new schema/data.
4. Test changes using both Docker Compose and standalone modes
