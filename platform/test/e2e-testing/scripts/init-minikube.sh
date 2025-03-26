#!/bin/bash

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

KAFKA_CONNECT_IMAGE="localhost/s3-custom-image:e2e-test"
KAFKA_CONNECT_SAVE_DIR="/tmp/images/kafka-connect"

#Load image tar from github action cache
echo "Loading image <$KAFKA_CONNECT_IMAGE>"
minikube image load "$KAFKA_CONNECT_SAVE_DIR/kafka-connect-image.tar"

cd "$DIR/platform/test/e2e-testing"

kubectl apply -f minikube/minio.yaml
kubectl apply -f minikube/kafka-core.yaml

kubectl wait statefulset kafka-cluster --for=condition=Available=True --timeout=60s
kubectl wait deployment kafka-schema-registry --for=condition=Available=True --timeout=10s
kubectl wait deployment kafka-ui --for=condition=Available=True --timeout=10s

kubectl get pods -A

#kubectl wait --for=condition=ready pod -l app=kafka-cluster
#kubectl wait --for=condition=ready pod -l app=kafka-schema-registry
#kubectl wait --for=condition=ready pod -l app=kafka-ui

echo "Waiting for kafka cluster to start"
kubectl logs -f kafka-cluster-0 | grep -qF "Kafka Server started"
kubectl logs -f kafka-cluster-1 | grep -qF "Kafka Server started"
kubectl logs -f kafka-cluster-2 | grep -qF "Kafka Server started"

kubectl apply -f minikube/kafka-connect.yaml
kubectl wait deployment kafka-connect --for=condition=Available=True --timeout=30s
kubectl wait deployment minio --for=condition=Available=True --timeout=10s

echo "Waiting for kafka connect to start"
kubectl logs -l app=kafka-connect -f | grep -qF "Kafka Connect started"

kubectl get pods -A
