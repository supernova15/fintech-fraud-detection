#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to scale deployments." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
DEPLOYMENT=${DEPLOYMENT:-fintech-fraud-detection}
REPLICAS=${REPLICAS:-${1:-}}

if [ -z "$REPLICAS" ]; then
  echo "Usage: REPLICAS=<count> $0 or $0 <count>" >&2
  exit 1
fi

kubectl -n "$NAMESPACE" scale deploy/"$DEPLOYMENT" --replicas="$REPLICAS"
