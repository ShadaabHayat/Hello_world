#!/bin/bash

brew install minikube podman

minikube start \
    --driver podman \
    --kubernetes-version v1.32.0 \
    --mount --mount-string "$(pwd):/e2e-code" \
    --memory 9000MB

bash "scripts/build-kafka-connect.sh"

bash "scripts/build-e2e-test-image.sh"

bash "scripts/init-minikube.sh"

bash "scripts/run-e2e-test.sh"

minikube stop
