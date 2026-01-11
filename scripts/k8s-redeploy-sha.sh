#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <image_tag_sha>"
  echo "Example: $0 8c3a1f2d9e0b..."
  exit 1
fi

TAG="$1"
NAMESPACE="${NAMESPACE:-fintech-fraud}"
DEPLOYMENT="${DEPLOYMENT:-fintech-fraud-detection}"
REGISTRY="${REGISTRY:-238479991718.dkr.ecr.us-east-1.amazonaws.com}"
REPO="${REPO:-fintech-fraud-detection}"
CONTAINER="${CONTAINER:-app}"

IMAGE="${REGISTRY}/${REPO}:${TAG}"

echo "[INFO] Setting image for ${DEPLOYMENT}/${CONTAINER} to ${IMAGE} in namespace ${NAMESPACE}..."
kubectl -n "${NAMESPACE}" set image "deploy/${DEPLOYMENT}" "${CONTAINER}=${IMAGE}"

echo "[INFO] Waiting for rollout..."
kubectl -n "${NAMESPACE}" rollout status "deploy/${DEPLOYMENT}"

echo "[INFO] Done."
kubectl -n "${NAMESPACE}" get pods -l app="${DEPLOYMENT}" -o wide || true
