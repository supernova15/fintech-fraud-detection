#!/usr/bin/env sh
set -e

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required to apply and restart deployments." >&2
  exit 1
fi

KUSTOMIZE_PATH=${KUSTOMIZE_PATH:-deploy/k8s/overlays/aws}
NAMESPACE=${NAMESPACE:-fintech-fraud}
DEPLOYMENT=${DEPLOYMENT:-fintech-fraud-detection}

kubectl apply -k "$KUSTOMIZE_PATH"
kubectl -n "$NAMESPACE" rollout restart deploy/"$DEPLOYMENT"
kubectl -n "$NAMESPACE" rollout status deploy/"$DEPLOYMENT"
