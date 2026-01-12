#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-fintech-fraud}"
APP_LABEL="${APP_LABEL:-fintech-fraud-detection}"
CONTAINER="${CONTAINER:-app}"
REMOTE_JAR_PATH="${REMOTE_JAR_PATH:-/app/app.jar}"
OUT="${1:-/tmp/fintech-app.jar}"

POD="$(kubectl -n "${NAMESPACE}" get pods -l app="${APP_LABEL}" \
  --field-selector=status.phase=Running \
  -o jsonpath='{.items[0].metadata.name}')"

echo "[INFO] Using pod: ${POD}"
echo "[INFO] Remote jar size:"
kubectl -n "${NAMESPACE}" exec "${POD}" -c "${CONTAINER}" -- sh -c "ls -lh ${REMOTE_JAR_PATH}; wc -c ${REMOTE_JAR_PATH}"

echo "[INFO] Dumping jar to ${OUT} via base64..."
kubectl -n "${NAMESPACE}" exec "${POD}" -c "${CONTAINER}" -- sh -c "base64 ${REMOTE_JAR_PATH}" \
  | base64 --decode > "${OUT}"

echo "[INFO] Local jar size:"
ls -lh "${OUT}"
file "${OUT}"

echo "[INFO] Verifying jar listing works..."
jar tf "${OUT}" | head -20

echo "[INFO] Done."
