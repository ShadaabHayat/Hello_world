#!/bin/bash

kubectl delete -f minikube/minio.yaml
kubectl wait deployment minio --for=delete --timeout=10s

kubectl apply -f minikube/minio.yaml
kubectl wait deployment minio --for=condition=Available=True --timeout=10s
