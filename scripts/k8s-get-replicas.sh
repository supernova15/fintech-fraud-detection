#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to query deployment replicas." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
DEPLOYMENT=${DEPLOYMENT:-fintech-fraud-detection}

kubectl -n "$NAMESPACE" get deploy "$DEPLOYMENT" -o jsonpath='{.spec.replicas}{"\n"}'
