#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to query pod status." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}

kubectl get pods -n "$NAMESPACE" -o wide
