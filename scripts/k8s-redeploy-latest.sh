#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-fintech-fraud}"
DEPLOYMENT="${DEPLOYMENT:-fintech-fraud-detection}"

echo "[INFO] Redeploying ${DEPLOYMENT} in namespace ${NAMESPACE} using :latest (rollout restart)..."

kubectl -n "${NAMESPACE}" rollout restart "deploy/${DEPLOYMENT}"
kubectl -n "${NAMESPACE}" rollout status "deploy/${DEPLOYMENT}"

echo "[INFO] Done."
kubectl -n "${NAMESPACE}" get pods -l app="${DEPLOYMENT}" -o wide || true
