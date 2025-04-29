#!/bin/bash

kubectl delete -f minikube/e2e-test-code.yaml
kubectl wait pod -l app=e2e-test --for=delete --timeout=10s

bash ./scripts/run-e2e-test.sh
