# AICORE Ingestion Engine Platform - End to End test framework

This project is part of the AICore ingestion engine, designed to provide a framework for testing custom SMT and other features of the ingestion engine pipeline, within a local environment.
The aim is to eliminate dependencies on other real environments while also helping developers reliably test their code locally before pushing it to the repo.

# 1. Overview

The End-to-End (E2E) test setup has a minikube node running locally. It has the following components running:
- 3-node Kafka Cluster
- Kafka Schema Registry
- Ingestion engine specific kafka-connect
- Kafka UI for debugging
- Minio for local S3 bucket

All the above components are deployed using kubernetes yaml files under **"minikube"** directory.

For GitHub Action runs, the workflow file takes care of everything
(starting minikube, building docker images, deploying components, running test code).

## 1.a. Run sequence

1. Minikube is initialized
2. Custom kafka connect docker image is built and saved to a file
3. E2E test docker image is built and saved to a file
4. Kafka components are deployed to minikube and await successfully deployment
5. E2E test job is deployed and logs are followed

# 2. Local Setup

The local setup will install homebrew package **minikube** and **podman**.

All bash scripts under **"scripts"** directory need to be run from the **"e2e-testing"** directory

## 2.a. First run

Running the script **"scripts/run-locally.sh"** should ideally work and take care of everything as mentioned above for GHA. But ofcourse local setups can have their own unique caveats.

## 2.b. Subsequent runs

If no custom SMT changes are to be tested, run the script **"scripts/rerun-e2e-test-without-code-changes.sh"**.
This will only redeploy the E2E test job and retain all other kafka deployments, thus saving time between runs.

## 2.c. Custom SMT code changes

If code changes to the custom SMT are to be tested, run the script **"scripts/rerun-e2e-test-with-code-changes.sh"**.
This will rebuild custom kafka-connect docker image, redeploy it.
On a successful deployment, it will also redeploy the E2E test job and retain all other kafka deployments (except for kafka-connect).

## 2.d. Adding new files/code to E2E
The **e2e-testing** folder is mounted in the E2E test job deployed on minikube. Thus, no specific steps are needed for test code changes.

## 2.e. Adding new dependencies in the E2E code
Add the required dependencies to the **requirements.txt** file.
Run the script **"scripts/rerun-e2e-test-with-test-dependencies-changes.sh"**.
This will rebuild the E2E test docker image, and redeploy the E2E test job.
All other kafka deployments will be retained.

# 3. Troubleshooting

## 3.a. Test fails with NoSuchBucket error
Minio can sometimes delete the test bucket after a laptop hibernation.
This can be remedied using the script **"scripts/recreate-minio-bucket.sh"**.

## 3.b. Failed to connect to ...
The test runs entirely offline, except for downloading the docker images.
Check if pods are in running state and their logs are not indicating any errors.
