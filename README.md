# Fintech Fraud Detection

Real-time, rule-based fraud detection service built with Java 21 and Spring Boot. It ingests
transactions from SQS, evaluates ordered rules, persists decisions via a DynamoDB-backed outbox,
and publishes decisions to SQS for downstream consumers. A gRPC API is included for synchronous
evaluation.

## Architecture

- SQS transaction -> SqsTransactionConsumer -> RuleEngine -> OutboxWriter (DynamoDB)
- OutboxPublisher -> decision SQS

Outbox publishing is at-least-once. Records are conditionally claimed before publish to reduce
double publishes across workers or instances.

## Design Notes

Tech choices and rationale:
- Java 21 + Spring Boot for JVM performance, dependency injection, and production-ready tooling.
- gRPC for low-latency synchronous evaluation and typed contracts (proto first).
- SQS for horizontal ingestion and DynamoDB outbox for durable, idempotent decision delivery.
- LocalStack + Testcontainers for opt-in integration coverage without cloud dependencies.

Idempotency and fallback:
- Outbox IDs use `transaction_id` when present, otherwise the SQS message id.
- Writer uses a conditional put so duplicate ingests do not create new outbox rows.
- Publisher conditionally claims records, retries with backoff, and marks records FAILED after
  `outbox.max-publish-attempts`.

Observability:
- Micrometer metrics for SQS processing and outbox publish/write latency and counts.
- CloudWatch metrics export is supported via Micrometer; logs include structured events.

GitHub Actions:
- `Run Tests` executes `./gradlew test` on pushes and PRs and uploads coverage/test reports.
- `Build and Push Image to ECR` builds a runtime image with OIDC auth and can deploy to EKS.

## Quickstart

Build and test:

```bash
./gradlew build
./gradlew test
```

Run locally:

```bash
./gradlew bootRun
```

Run with LocalStack (SQS + DynamoDB):

```bash
docker compose -f docker-compose.localstack.yml up
./gradlew bootRun --args='--spring.profiles.active=localstack'
./scripts/send-sqs-proto.sh
```

## Testing

Testing scenarios:

- Unit tests validate rule evaluation, SQS processing, and outbox behavior with mocks.
- LocalStack integration tests validate SQS + DynamoDB wiring end to end (opt-in).
- Manual local flow validates developer setup and basic message processing.
- Resilience tests validate pod failure recovery and at-least-once guarantees.

Unit tests:

```bash
./gradlew test
```

LocalStack integration tests (opt-in):

```bash
RUN_LOCALSTACK_TESTS=true ./gradlew test
```

Manual local flow:

```bash
docker compose -f docker-compose.localstack.yml up
./gradlew bootRun --args='--spring.profiles.active=localstack'
./scripts/send-sqs-proto.sh
```

CI runs unit tests and uploads coverage/test reports (see `.github/workflows/ci-tests.yml`).

## Rule Engine

Rules are evaluated in order. A default approve rule is always present so evaluation returns a
decision instead of throwing on fallthrough.

## Outbox Publisher

- Poller dispatches pending records to a worker pool.
- Each record is claimed with a conditional update before publish.
- Configure worker count and claim lease:
  - `outbox.publish-workers` (default `2`)
  - `outbox.publish-claim-lease-millis` (default `5000`)

## Configuration

Key properties (see `src/main/resources/application.properties` for all values):

- `sqs.*` for transaction queue settings
- `outbox.*` for DynamoDB table, decision queue, and publish behavior
- `fraud.rules.*` for rule thresholds

## Docs

- Local testing: `localtesting.md`
- Deployment notes: `deployment.md`
- Production testing checklist: `production_testing.md`
- Resilience testing: `resilience-testing.md`
- Demo walkthrough: `demo_final.mp4`

## Future Improvements

- DLQ/redrive policy support for SQS ingestion with explicit retry handling.
- Outbox negative-path integration test coverage for publish failures.
- Document DynamoDB outbox schema, capacity planning, and operational runbook.
- Expand observability with structured decision sampling and per-rule evaluation metrics.
- Kubernetes HPA tuning and autoscaling policy based on SQS depth and CPU/memory.
- Optional cache layer for hot rule parameters or feature flags to reduce config churn.
- Review DynamoDB indexing strategy (status + created_at) for scale and query patterns.
