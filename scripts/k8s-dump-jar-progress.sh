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

echo "[INFO] POD=${POD}"
echo "[INFO] Remote jar:"
kubectl -n "${NAMESPACE}" exec "${POD}" -c "${CONTAINER}" -- sh -c "ls -lh ${REMOTE_JAR_PATH}; wc -c ${REMOTE_JAR_PATH}"

echo "[INFO] Streaming jar to ${OUT} (with progress)..."
kubectl -n "${NAMESPACE}" exec "${POD}" -c "${CONTAINER}" -- sh -c "cat ${REMOTE_JAR_PATH}" \
| python3 -c '
import sys, time
out_path = sys.argv[1]
chunk = 1024 * 1024  # 1MiB
total = 0
last = time.time()
with open(out_path, "wb") as f:
    while True:
        b = sys.stdin.buffer.read(chunk)
        if not b:
            break
        f.write(b)
        total += len(b)
        now = time.time()
        if now - last >= 1.0:
            print(f"[PROGRESS] {total/1024/1024:.1f} MiB", flush=True)
            last = now
print(f"[DONE] wrote {total} bytes to {out_path}", flush=True)
' "${OUT}"

echo "[INFO] Local jar:"
ls -lh "${OUT}"
file "${OUT}"
jar tf "${OUT}" | head -20

echo "[INFO] Done."
