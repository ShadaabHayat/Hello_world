#!/bin/bash

# Create reports directory
mkdir -p /reports
chmod 777 /reports

# Default to Avro if SCHEMA_TYPE not specified
SCHEMA_TYPE=${SCHEMA_TYPE:-"avro"}

if [ "${SCHEMA_TYPE,,}" = "both" ]; then
    echo "Running both Avro and JSON schema implementations..."


    echo "Running Avro schema implementation..."
    SCHEMA_TYPE=avro k6 run /app/scripts/main.js

    # Run with JSON schema
    echo "Running JSON schema implementation..."
    SCHEMA_TYPE=json k6 run /app/scripts/main.js
else
    # Run with specified schema type (defaults to Avro)
    echo "Running with schema type: ${SCHEMA_TYPE}..."
    k6 run /app/scripts/main.js
fi

echo "All tests completed successfully"

# Keep container running
while true; do
  sleep 21600
done
