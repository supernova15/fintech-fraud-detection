#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-fintech-fraud}"
DEPLOYMENT="${DEPLOYMENT:-fintech-fraud-detection}"
REPLICAS="${REPLICAS:-1}"

echo "[INFO] Hard reset: scaling ${DEPLOYMENT} to 0..."
kubectl -n "${NAMESPACE}" scale "deploy/${DEPLOYMENT}" --replicas=0

echo "[INFO] Waiting for pods to be deleted..."
kubectl -n "${NAMESPACE}" wait --for=delete pod -l app="${DEPLOYMENT}" --timeout=180s || true

echo "[INFO] Scaling ${DEPLOYMENT} back to ${REPLICAS}..."
kubectl -n "${NAMESPACE}" scale "deploy/${DEPLOYMENT}" --replicas="${REPLICAS}"

echo "[INFO] Waiting for rollout..."
kubectl -n "${NAMESPACE}" rollout status "deploy/${DEPLOYMENT}"

echo "[INFO] Done."
kubectl -n "${NAMESPACE}" get pods -l app="${DEPLOYMENT}" -o wide || true
