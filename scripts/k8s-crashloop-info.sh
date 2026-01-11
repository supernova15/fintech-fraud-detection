#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to inspect pods." >&2
  exit 1
fi

NAMESPACE=${NAMESPACE:-fintech-fraud}
POD=${POD:-${1:-}}
CONTAINER=${CONTAINER:-app}

if [ -z "$POD" ]; then
  echo "Usage: POD=<pod-name> $0 or $0 <pod-name>" >&2
  exit 1
fi

if ! kubectl -n "$NAMESPACE" logs "$POD" -c "$CONTAINER" --previous --tail=200; then
  kubectl -n "$NAMESPACE" logs "$POD" -c "$CONTAINER" --tail=200
fi

kubectl -n "$NAMESPACE" describe pod "$POD" | sed -n '/State:/,/Events:/p'
