# Kubernetes deployment

Base manifests live in `deploy/k8s/base`. Use the overlays for AWS and AWS China:

```sh
kubectl apply -k deploy/k8s/overlays/aws
kubectl apply -k deploy/k8s/overlays/aws-cn
```

Before applying, update the image registry and tag in:

- `deploy/k8s/overlays/aws/kustomization.yaml`
- `deploy/k8s/overlays/aws-cn/kustomization.yaml`

You can also adjust rule thresholds and thread pool settings in:

- `deploy/k8s/base/configmap.yaml`

Outbox and SQS settings live in the same ConfigMap. Ensure you provide:
- `sqs.queue-url`, `sqs.region`
- `outbox.table-name`, `outbox.decision-queue-url`, and AWS credentials/region overrides if needed

CloudWatch metrics can be enabled via environment variables in
`deploy/k8s/base/configmap.yaml` (`MANAGEMENT_METRICS_EXPORT_CLOUDWATCH2_*`).

For CloudWatch Logs, apply the Fluent Bit add-on:
```
kubectl apply -k deploy/k8s/addons/cloudwatch-logs
```
