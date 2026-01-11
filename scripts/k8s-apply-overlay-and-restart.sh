#!/usr/bin/env bash
set -euo pipefail

OVERLAY_DIR="${1:-deploy/k8s/overlays/aws}"
NAMESPACE="${NAMESPACE:-fintech-fraud}"
DEPLOYMENT="${DEPLOYMENT:-fintech-fraud-detection}"

echo "[INFO] Applying kustomize overlay: ${OVERLAY_DIR}"
kubectl apply -k "${OVERLAY_DIR}"

echo "[INFO] Restarting deployment ${DEPLOYMENT} in namespace ${NAMESPACE}..."
kubectl -n "${NAMESPACE}" rollout restart "deploy/${DEPLOYMENT}"
kubectl -n "${NAMESPACE}" rollout status "deploy/${DEPLOYMENT}"

echo "[INFO] Done."
kubectl -n "${NAMESPACE}" get pods -l app="${DEPLOYMENT}" -o wide || true
