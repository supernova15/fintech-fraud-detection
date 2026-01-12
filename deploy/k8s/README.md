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

## Outbox pattern

This service uses a DynamoDB-backed outbox to make decision publishing reliable:
- The SQS consumer writes a decision record to the outbox before deleting the SQS message.
- `outbox_id` is `transaction_id` (fallback `message_id`) with a conditional write for idempotency.
- The outbox publisher polls `PENDING` records, publishes to the decision queue, and updates status to
  `PUBLISHED` or `FAILED` after `outbox.max-publish-attempts`.
- This is at-least-once delivery; downstream consumers should dedupe on `transaction_id`.

CloudWatch metrics can be enabled via environment variables in
`deploy/k8s/base/configmap.yaml` (`MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_*`).

For CloudWatch Logs, apply the Fluent Bit add-on:
```
kubectl apply -k deploy/k8s/addons/cloudwatch-logs
```
