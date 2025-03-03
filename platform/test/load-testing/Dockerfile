FROM python:3.9-slim

WORKDIR /app

# Copy requirements first to leverage Docker cache
COPY requirements.txt .
RUN pip install --no-cache-dir -v -r requirements.txt

# Copy source code
COPY src/ .

# Set default values for Locust configuration
ENV LOCUST_FILE=locustfile.py \
    LOCUST_HOST=http://localhost \
    LOCUST_USERS=100 \
    LOCUST_SPAWN_RATE=2 \
    LOCUST_RUN_TIME=3600 \
    LOCUST_HEADLESS=true

# Command to run Locust with environment variables
CMD locust -f ${LOCUST_FILE} \
    --host ${LOCUST_HOST} \
    -u ${LOCUST_USERS} \
    -r ${LOCUST_SPAWN_RATE} \
    --run-time ${LOCUST_RUN_TIME}s \
    $(if [ "$LOCUST_HEADLESS" = "true" ]; then echo "--headless"; fi)
