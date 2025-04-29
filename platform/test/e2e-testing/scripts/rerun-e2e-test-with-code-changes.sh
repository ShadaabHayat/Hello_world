#!/bin/bash

#Save image tar here for github action caching
KAFKA_CONNECT_SAVE_DIR="/tmp/images/kafka-connect"
mkdir -p "$KAFKA_CONNECT_SAVE_DIR"

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

source "scripts/custom-docker-cmd.source"

KAFKA_CONNECT_IMAGE="localhost/s3-custom-image:e2e-test"

cd "$DIR/resources/kafka-connect"
docker build -t "$KAFKA_CONNECT_IMAGE" .

echo "Saving image <$KAFKA_CONNECT_IMAGE>"
mkdir -p "$KAFKA_CONNECT_SAVE_DIR"
docker image save "$KAFKA_CONNECT_IMAGE" > "$KAFKA_CONNECT_SAVE_DIR/kafka-connect-image.tar"

minikube image load "$KAFKA_CONNECT_SAVE_DIR/kafka-connect-image.tar"

cd "$DIR/platform/test/e2e-testing"

kubectl delete -f minikube/kafka-connect.yaml
kubectl wait deployment kafka-connect --for=delete --timeout=15s

kubectl apply -f minikube/kafka-connect.yaml

before=$(date +%s)
kubectl wait deployment kafka-connect --for=condition=Available=True --timeout=30s
echo "Waiting for kafka connect to start"
kubectl logs -l app=kafka-connect -f | grep -qF "Kafka Connect started"
after=$(date +%s)

kubectl delete -f minikube/e2e-test-code.yaml
kubectl wait pod -l app=e2e-test --for=delete --timeout=10s

bash ./scripts/run-e2e-test.sh
