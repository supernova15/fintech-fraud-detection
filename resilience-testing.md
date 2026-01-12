# Resilience Testing

This guide covers pod-failure resilience testing using the provided script.

## Prerequisites

- `kubectl` access to the target cluster/namespace.
- SQS + DynamoDB configured (AWS or LocalStack).
- The service deployed and processing transactions.
- `aws` or `awslocal` CLI available (the script will use docker compose LocalStack if needed).

## Script

`scripts/resilience-pod-kill.sh` sends a burst of transactions while deleting pods, then drains the
decision queue to verify all decisions arrive. It is destructive to the decision queue (it deletes
messages after reading).

### Example: EKS/AWS

```bash
NAMESPACE=fintech-fraud \
APP_LABEL=app=fintech-fraud-detection \
TOTAL_MESSAGES=50 \
KILL_COUNT=3 \
KILL_INTERVAL_SECS=5 \
QUEUE_NAME=fintech-transactions \
DECISIONS_QUEUE_NAME=fintech-decisions \
REGION=us-east-1 \
AWS_PROFILE=prod \
scripts/resilience-pod-kill.sh
```

### Example: LocalStack

```bash
docker compose -f docker-compose.localstack.yml up
./gradlew bootRun --args='--spring.profiles.active=localstack'

NAMESPACE=fintech-fraud \
APP_LABEL=app=fintech-fraud-detection \
TOTAL_MESSAGES=25 \
KILL_COUNT=2 \
KILL_INTERVAL_SECS=5 \
QUEUE_NAME=fintech-transactions \
DECISIONS_QUEUE_NAME=fintech-decisions \
DRAIN_BEFORE=true \
scripts/resilience-pod-kill.sh
```

## Pass/Fail Criteria

Pass:
- All `TOTAL_MESSAGES` decisions are received within `DECISION_TIMEOUT_SECS`.
- No stuck `PENDING` outbox records after the system settles.
- Service returns to steady state (pods running, queue depth stabilizes).

Fail:
- Fewer decision messages than sent after timeout.
- Persistent `PENDING` outbox records beyond expected retry windows.
- Crash loops or repeated failures after restart.

## Notes

- At-least-once delivery means duplicates are possible; the test checks for missing decisions, not
  uniqueness.
- Use `DRAIN_BEFORE=true` if the decision queue might have old messages.
