# Deployment

This guide covers building the container image and deploying the service to
Kubernetes using the provided kustomize overlays.

## Prerequisites

- `kubectl` configured for your target cluster.
- Docker for building the image.
- AWS credentials available to the cluster (IRSA, node role, or env vars).
- AWS CLI locally if you plan to build/push to ECR.

## GitHub Actions (ECR build + optional deploy)

The workflow in `.github/workflows/ecr-push.yml` builds and pushes a runtime
image to ECR on pushes to `main`. It also has a manual `workflow_dispatch`
option to deploy to EKS.

Required repository variables (Settings -> Secrets and variables -> Actions):
- `AWS_ROLE_ARN` (OIDC role to assume)
- `AWS_REGION` (defaults to `us-east-1`)
- `ECR_REPOSITORY` (defaults to `fintech-fraud-detection`)
- `EKS_CLUSTER_NAME` (required only for the deploy job)
- `K8S_NAMESPACE` (optional, defaults to `fintech-fraud`)
- `K8S_OVERLAY` (optional, defaults to `deploy/k8s/overlays/aws`)

The IAM role used by GitHub Actions must trust your forked repo and allow ECR
push plus EKS access to apply manifests and set images. If you use IAM auth for
Kubernetes, ensure the role is mapped to the cluster RBAC.

### Forking + reproducing the workflow

1) Fork the repo and enable Actions in your fork if disabled.
2) Create or reuse an ECR repository matching `ECR_REPOSITORY`.
3) Create an IAM role for GitHub OIDC with trust conditions scoped to your
   fork (branch/tag as needed) and grant ECR + EKS permissions.
4) Set the Actions variables listed above in your fork.
5) Ensure the Kubernetes namespace and config map values match your
   environment (`deploy/k8s/base/configmap.yaml`).
6) Push to `main` to build/push, then use `Run workflow` with `deploy=true` to
   apply the manifests and update the image.

## Build and push the image (ECR)

The `scripts/ecr.sh` helper builds the Docker image and pushes `:latest` to ECR.
It expects `AWS_ACCOUNT_ID` and `AWS_REGION` to be set.

```
AWS_REGION=us-east-1 AWS_ACCOUNT_ID=123456789012 AWS_PROFILE=prod \
bash scripts/ecr.sh
```

If you use a non-`latest` tag, update the image tag in the kustomize overlay
before applying.

## Configure Kubernetes manifests

1) Update the image reference:
- `deploy/k8s/overlays/aws/kustomization.yaml`
- `deploy/k8s/overlays/aws-cn/kustomization.yaml` (if deploying to AWS China)

2) Update runtime configuration:
- `deploy/k8s/base/configmap.yaml`
  - `SQS_QUEUE_URL`, `SQS_REGION`
  - `OUTBOX_TABLE_NAME`, `OUTBOX_DECISION_QUEUE_URL`, `OUTBOX_REGION`
  - CloudWatch metrics: `MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_*`

3) Ensure the namespace exists:
```
kubectl create namespace fintech-fraud
```

4) If using IRSA, attach permissions similar to
`deploy/aws/irsa/policy.template.json` to the service account used by the
deployment.

## CloudWatch logs (Fluent Bit)

1) Create an IAM role for the log forwarder using
`deploy/aws/irsa/cloudwatch-logs-policy.template.json`.

2) Update the service account annotation in
`deploy/k8s/addons/cloudwatch-logs/serviceaccount.yaml` with the role ARN.

3) Adjust log settings in `deploy/k8s/addons/cloudwatch-logs/daemonset.yaml`:
`AWS_REGION`, `LOG_GROUP_NAME`, `LOG_STREAM_PREFIX`.

4) Apply the add-on:
```
kubectl apply -k deploy/k8s/addons/cloudwatch-logs
```

## CloudWatch metrics (Micrometer)

The app exports Micrometer metrics to CloudWatch when
`MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_ENABLED=true`.

- Ensure the app's IAM role includes `cloudwatch:PutMetricData`
  (included in `deploy/aws/irsa/policy.template.json`).
- Set the region/namespace in `deploy/k8s/base/configmap.yaml`.
- After traffic flows, confirm metrics appear under the namespace (CLI example):
```
aws cloudwatch list-metrics --namespace Fintech/FraudDetection --region us-east-1
```

## Apply manifests

Apply the overlay directly:
```
kubectl apply -k deploy/k8s/overlays/aws
```

Or use the helper to apply and restart:
```
NAMESPACE=fintech-fraud \
./scripts/k8s-apply-overlay-and-restart.sh deploy/k8s/overlays/aws
```

The AWS overlays set the Service type to `LoadBalancer`.

## Redeploy with a new image tag

Option A: update `newTag` in the overlay and re-apply.

Option B: set the image by tag SHA:
```
NAMESPACE=fintech-fraud \
REGISTRY=123456789012.dkr.ecr.us-east-1.amazonaws.com \
REPO=fintech-fraud-detection \
./scripts/k8s-redeploy-sha.sh <image_tag_sha>
```

## Verify

```
kubectl -n fintech-fraud rollout status deploy/fintech-fraud-detection
kubectl -n fintech-fraud get pods -l app=fintech-fraud-detection
kubectl -n fintech-fraud get svc fintech-fraud-detection -o wide
```
