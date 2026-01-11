#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to query ReplicaSets." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
APP_LABEL=${APP_LABEL:-app=fintech-fraud-detection}

kubectl -n "$NAMESPACE" get rs -l "$APP_LABEL"
