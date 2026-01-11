#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to delete pods." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
APP_LABEL=${APP_LABEL:-app=fintech-fraud-detection}

kubectl -n "$NAMESPACE" delete pod -l "$APP_LABEL"
