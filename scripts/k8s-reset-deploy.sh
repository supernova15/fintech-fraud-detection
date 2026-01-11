#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to reset deployments." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
DEPLOYMENT=${DEPLOYMENT:-fintech-fraud-detection}
APP_LABEL=${APP_LABEL:-app=fintech-fraud-detection}
TARGET_REPLICAS=${TARGET_REPLICAS:-1}

kubectl -n "$NAMESPACE" scale deploy/"$DEPLOYMENT" --replicas=0
kubectl -n "$NAMESPACE" wait --for=delete pod -l "$APP_LABEL" --timeout=120s || true
kubectl -n "$NAMESPACE" scale deploy/"$DEPLOYMENT" --replicas="$TARGET_REPLICAS"
kubectl -n "$NAMESPACE" rollout status deploy/"$DEPLOYMENT"
