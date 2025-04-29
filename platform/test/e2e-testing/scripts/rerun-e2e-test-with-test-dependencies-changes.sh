#!/bin/bash

#Save image tar here for github action caching
#KAFKA_CONNECT_SAVE_DIR="/tmp/images/kafka-connect"
E2E_TEST_SAVE_DIR="/tmp/images/e2e-test"
mkdir -p "$E2E_TEST_SAVE_DIR"

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

source "scripts/custom-docker-cmd.source"

E2E_TEST_IMAGE="localhost/python:e2e-test"

cd "$DIR/platform/test/e2e-testing"
docker build -t "$E2E_TEST_IMAGE" .

echo "Saving image <$E2E_TEST_IMAGE>"
mkdir -p "$E2E_TEST_SAVE_DIR"
docker image save "$E2E_TEST_IMAGE" > "$E2E_TEST_SAVE_DIR/e2e-image.tar"

kubectl delete -f minikube/e2e-test-code.yaml
kubectl wait pod -l app=e2e-test --for=delete --timeout=10s

bash ./scripts/run-e2e-test.sh
