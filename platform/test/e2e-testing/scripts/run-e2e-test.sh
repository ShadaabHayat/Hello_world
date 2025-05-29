#!/bin/bash

if [ -z "$GITHUB_ACTIONS" ]; then
    DIR="$(pwd)/../../.."
else
    DIR="$GITHUB_WORKSPACE"
fi

cd "$DIR/platform/test/e2e-testing"

kubectl apply -f minikube/e2e-test-code.yaml
kubectl wait --for=condition=Ready=True pod -l app=e2e-test  --timeout=10s
kubectl logs -l app=e2e-test -f

while true; do {
    stat=$(kubectl get pod -l app=e2e-test -o json | jq '.items[0].status.containerStatuses[0].state | keys[0]' -r)
    if [ "$stat" = "running" ] || [ "$stat" = "waiting" ]; then
        sleep 1
    elif [ "$stat" = "terminated" ]; then
        exit_code=$(kubectl get pod -l app=e2e-test -o json | jq '.items[0].status.containerStatuses[0].state.terminated.exitCode' -r)
        if [ "$exit_code" -eq 0 ]; then
            echo "All tests passed!"
        else
            echo "E2E test code failed. Exiting with exit code <$exit_code>"
        fi
        exit "$exit_code"
    else
        echo "Unexpected pod state: <$exit_code>, exiting"
        exit 255
    fi
}; done
