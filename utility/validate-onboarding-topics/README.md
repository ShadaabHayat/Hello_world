# Validate Data Ingestion Onboarding Topic Details

This Python script validates a list of Kafka topics by verifying their presence, message availability, and the existence of a registered schema. The output is saved to a CSV file in an `output` folder.

## Prerequisites

Requires AWS credentials and port forwarding already setup in your local environment

* Kubectl command:
        ```bash
        kubectl port-forward svc/kafka-ui-service 8080:8080 -n common
        ```

## Setup

1. **Navigate to the Project Directory**

      ```bash
      cd validate-onboarding-topics
      ```

2. **Create Virtual Environment:**
    * Create a virtual environment:

      ```bash
      python -m venv .venv
      ```

    * Activate the virtual environment:
      * On Linux/macOS:

        ```bash
        source .venv/bin/activate
        ```

      * On Windows:

        ```bash
        .\.venv\Scripts\activate
        ```

    * Your terminal prompt should now be prefixed with `(.venv)`.

3. **Install Dependencies:** Install the required Python libraries using pip and the provided `requirements.txt` file:

    ```bash
    pip install -r requirements.txt
    ```

4. **Update `topics.txt`:** Inside the `config` directory, update the list of topics to be checked in the text file named `topics.txt`.

    ```
    uz_resourcedb.public.policies_condition
    uz_authdb.public.users_usersession
    uz_resourcedb.public.locations_devicegroup
    ```

## Usage

  **Run the script:**
    ```bash
    python kafka_health_check.py
    ```

## Output

The script will generate a CSV file named `onboarding-issues.csv` inside a newly created (if it doesn't exist) `output` folder in the same directory where you run the script. Only topics with issues are listed. This file will contain a report with the following columns:

* **Topic Name**: The name of the Kafka topic.
* **Is Present**: `True` if the topic exists in the Kafka cluster, `False` otherwise.
* **Has Messages**: `True` if the topic exists and its maximum offset is greater than 0, `False` otherwise.
* **Has Schema**: `True` if a schema is found in the registered schemas that starts with the topic name followed by a hyphen or is exactly the topic name, `False` otherwise.

The script will also print a message to the console indicating the location of the generated CSV file.

## Configuration

The Kafka URL defaults to `http://localhost:8080`

* Use the `--api-base-url` command-line argument when running the script to override the base URL for your Kafka and Schema Registry APIs.
