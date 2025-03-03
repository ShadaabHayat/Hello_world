# Load Testing Application

This application is designed to perform load testing on the AICore database system using Locust, targeting both the resources and authentication databases. It generates realistic test data and simulates various database operations to measure system performance under load.

## What it Does

The load testing application:

- Generates realistic test data for various entities (devices, services, users, etc.)
- Simulates concurrent database operations
- Measures response times and system performance
- Tests database scalability and reliability
- Provides real-time metrics through Locust's web interface

## Target Tables

The application interacts with two databases:

### Resources Database Tables

- `resources_device`: Stores device information and status
- `resources_devicegroup`: Manages device grouping
- `resources_service`: Contains service configurations
- `resources_project`: Stores project information
- `resources_devicegroup_devices`: Maps devices to device groups
- `resources_project_service`: Maps services to projects
- `resources_policy`: Stores policy configurations

For detailed schema information, refer to `resource-tables.sql`.

### Auth Database Tables

- `users_user`: Stores user information and authentication details
- `users_useraccessgroup`: Manages user access group assignments

For detailed schema information, refer to `auth-tables.sql`.

## Application Components

### Data Generators (`src/data_generators.py`)

- Generates realistic test data for all entities
- Uses Faker library for creating realistic data
- Ensures data consistency and uniqueness
- Handles relationships between different entities

### Database Operations (`src/db_operations.py`)

- Manages database connections
- Handles database transactions
- Implements CRUD operations for all entities
- Includes error handling and connection retry logic

### Load Testing (`src/locustfile.py`)

- Defines Locust user behavior
- Implements test scenarios
- Manages test execution flow
- Collects performance metrics

## Running the Application

### Prerequisites

- Docker installed
- Kubernetes cluster (for k8s deployment)
- Access to target databases
- Required environment variables configured

### Local Development with Docker

1. Clone the repository:

   ```bash
   git clone <repository-url>
   cd ingestion-engine/load-testing
   ```

2. Build the Docker image:

   ```bash
   docker buildx build --platform linux/amd64 -t jawadahmadd/load-testing:latest .
   ```

3. Run the container locally:

   ```bash
   docker run -e resource_db=postgres \
             -e resource_user=postgres \
             -e resource_password=1234 \
             -e resource_host=localhost \
             -e resource_port=5433 \
             -e auth_db=postgres \
             -e auth_user=postgres \
             -e auth_password=1234 \
             -e auth_host=localhost \
             -e auth_port=5433 \
             jawadahmadd/load-testing:latest
   ```

### Kubernetes Deployment

1. Ensure you have the necessary secrets in your Kubernetes cluster:

   ```bash
   # Create secrets for resources database
   kubectl create secret generic aicore-rds-creds-resource \
     --from-literal=postgres_db=your_db \
     --from-literal=postgres_user=your_user \
     --from-literal=postgres_password=your_password \
     --from-literal=postgres_host=your_host \
     --from-literal=postgres_port=your_port \
     -n aicore

   # Create secrets for auth database
   kubectl create secret generic aicore-rds-creds-auth \
     --from-literal=postgres_db=your_db \
     --from-literal=postgres_user=your_user \
     --from-literal=postgres_password=your_password \
     --from-literal=postgres_host=your_host \
     --from-literal=postgres_port=your_port \
     -n aicore
   ```

2. Apply the Kubernetes deployment:

   ```bash
   kubectl apply -f k8s/deployment.yaml
   ```

3. Check the deployment status:

   ```bash
   kubectl get pods -n aicore
   ```

4. View logs:

   ```bash
   kubectl logs -f deployment/locust-load-test -n aicore
   ```

## Configuration

### Environment Variables

#### Resources Database

- `resource_db`: Database name
- `resource_user`: Database user
- `resource_password`: Database password
- `resource_host`: Database host
- `resource_port`: Database port

#### Auth Database

- `auth_db`: Database name
- `auth_user`: Database user
- `auth_password`: Database password
- `auth_host`: Database host
- `auth_port`: Database port

### Load Testing Parameters

The load testing behavior can be configured through environment variables:

- `LOCUST_FILE`: The locust file to run (default: `locustfile.py`)
- `LOCUST_HOST`: The host to load test (default: `http://localhost`)
- `LOCUST_USERS`: Number of users to simulate (default: `100`)
- `LOCUST_SPAWN_RATE`: Rate at which users are spawned (default: `2`)
- `LOCUST_RUN_TIME`: Duration of the test in seconds (default: `3600`)
- `LOCUST_HEADLESS`: Run in headless mode without web UI (default: `true`)

Example of running with custom configuration:

```bash
docker run \
  -e LOCUST_USERS=200 \
  -e LOCUST_SPAWN_RATE=5 \
  -e LOCUST_RUN_TIME=7200 \
  -e resource_db=postgres \
  -e resource_user=postgres \
  -e resource_password=1234 \
  -e resource_host=localhost \
  -e resource_port=5433 \
  -e auth_db=postgres \
  -e auth_user=postgres \
  -e auth_password=1234 \
  -e auth_host=localhost \
  -e auth_port=5433 \
  jawadahmadd/load-testing:latest
```

For Kubernetes deployment, add these environment variables to the deployment.yaml:

```yaml
env:
- name: LOCUST_FILE
   value: "locustfile.py"
- name: LOCUST_HOST
   value: "http://localhost"
- name: LOCUST_USERS
   value: "50"
- name: LOCUST_SPAWN_RATE
   value: "2"
- name: LOCUST_RUN_TIME
   value: "3600"
- name: LOCUST_HEADLESS
   value: "true"
```

## Troubleshooting

### Common Issues

1. Database Connection Errors
   - Verify environment variables
   - Check database accessibility
   - Ensure proper network configuration

2. Data Generation Issues
   - Check for unique constraint violations
   - Verify data generator configurations
   - Review error logs for specific issues

3. Performance Issues
   - Monitor database resource usage
   - Check network latency
   - Review connection pool settings
