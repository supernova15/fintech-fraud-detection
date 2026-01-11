#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to query pod status." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
APP_LABEL=${APP_LABEL:-app=fintech-fraud-detection}

kubectl -n "$NAMESPACE" get pods -l "$APP_LABEL" \
  -o custom-columns=NAME:.metadata.name,RS:.metadata.ownerReferences[0].name,STATUS:.status.phase,RESTARTS:.status.containerStatuses[0].restartCount
