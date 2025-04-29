#!/bin/bash

#Save image tar here for github action caching
SAVE_DIR="/tmp/images/e2e-test"
mkdir -p "$SAVE_DIR"

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

E2E_TEST_IMAGE="localhost/python:e2e-test"

source "scripts/custom-docker-cmd.source"

cd "$DIR/platform/test/e2e-testing"
docker build -t "$E2E_TEST_IMAGE" .

echo "Saving image <$E2E_TEST_IMAGE>"
mkdir -p "$SAVE_DIR"
docker image save "$E2E_TEST_IMAGE" > "$SAVE_DIR/e2e-image.tar"
