#!/bin/bash

#Save image tar here for github action caching
SAVE_DIR="/tmp/images/kafka-connect"
mkdir -p "$SAVE_DIR"

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

KAFKA_CONNECT_IMAGE="localhost/s3-custom-image:e2e-test"

source "scripts/custom-docker-cmd.source"

cd "$DIR/resources/kafka-connect"
docker build -t "$KAFKA_CONNECT_IMAGE" .

echo "Saving image <$KAFKA_CONNECT_IMAGE>"
mkdir -p "$SAVE_DIR"
docker image save "$KAFKA_CONNECT_IMAGE" > "$SAVE_DIR/kafka-connect-image.tar"
