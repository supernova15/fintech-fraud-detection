#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-fintech-fraud}"
DEPLOYMENT="${DEPLOYMENT:-fintech-fraud-detection}"
CONTAINER_INDEX="${CONTAINER_INDEX:-0}"

echo "[INFO] Patching imagePullPolicy=Always for deploy/${DEPLOYMENT} (container index ${CONTAINER_INDEX})..."

kubectl -n "${NAMESPACE}" patch deploy "${DEPLOYMENT}" --type='json' -p="[
  {\"op\":\"add\",\"path\":\"/spec/template/spec/containers/${CONTAINER_INDEX}/imagePullPolicy\",\"value\":\"Always\"}
]"

echo "[INFO] Restarting deployment..."
kubectl -n "${NAMESPACE}" rollout restart "deploy/${DEPLOYMENT}"
kubectl -n "${NAMESPACE}" rollout status "deploy/${DEPLOYMENT}"

echo "[INFO] Done."
