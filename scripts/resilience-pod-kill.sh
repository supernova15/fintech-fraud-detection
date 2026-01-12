#!/usr/bin/env sh
set -e

# Resilience test: send transactions while killing pods, then verify decisions arrive.
# This script is destructive to the decision queue (it consumes messages).
#
# Common overrides:
#   NAMESPACE, APP_LABEL, TOTAL_MESSAGES, TX_ID_PREFIX
#   KILL_COUNT, KILL_INTERVAL_SECS, START_DELAY_SECS
#   QUEUE_NAME, DECISIONS_QUEUE_NAME, QUEUE_URL, DECISIONS_QUEUE_URL
#   REGION, AWS_PROFILE, DRAIN_BEFORE

NAMESPACE=${NAMESPACE:-fintech-fraud}
APP_LABEL=${APP_LABEL:-app=fintech-fraud-detection}
TOTAL_MESSAGES=${TOTAL_MESSAGES:-25}
TX_ID_PREFIX=${TX_ID_PREFIX:-resilience-}
KILL_COUNT=${KILL_COUNT:-2}
KILL_INTERVAL_SECS=${KILL_INTERVAL_SECS:-5}
START_DELAY_SECS=${START_DELAY_SECS:-2}
DECISION_TIMEOUT_SECS=${DECISION_TIMEOUT_SECS:-120}
QUEUE_NAME=${QUEUE_NAME:-fintech-transactions}
DECISIONS_QUEUE_NAME=${DECISIONS_QUEUE_NAME:-fintech-decisions}
QUEUE_URL=${QUEUE_URL:-}
DECISIONS_QUEUE_URL=${DECISIONS_QUEUE_URL:-}
REGION=${REGION:-${AWS_REGION:-us-east-1}}
DRAIN_BEFORE=${DRAIN_BEFORE:-false}

if command -v awslocal >/dev/null 2>&1; then
  AWS_CMD="awslocal"
elif command -v aws >/dev/null 2>&1; then
  AWS_CMD="aws"
elif command -v docker >/dev/null 2>&1; then
  AWS_CMD="docker compose -f docker-compose.localstack.yml exec -T localstack awslocal"
else
  echo "Missing awslocal or aws CLI (and docker compose fallback unavailable)" >&2
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "Missing kubectl" >&2
  exit 1
fi

AWS_ARGS="--region $REGION"
if [ -n "${AWS_PROFILE:-}" ]; then
  AWS_ARGS="$AWS_ARGS --profile $AWS_PROFILE"
fi

resolve_queue_url() {
  queue_name="$1"
  ${AWS_CMD} sqs get-queue-url \
    --queue-name "$queue_name" \
    --query 'QueueUrl' \
    --output text \
    ${AWS_ARGS}
}

if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL=$(resolve_queue_url "$QUEUE_NAME")
fi

if [ -z "$DECISIONS_QUEUE_URL" ]; then
  DECISIONS_QUEUE_URL=$(resolve_queue_url "$DECISIONS_QUEUE_NAME")
fi

if [ -z "$QUEUE_URL" ] || [ "$QUEUE_URL" = "None" ]; then
  echo "Missing QUEUE_URL. Set QUEUE_URL or QUEUE_NAME to resolve it." >&2
  exit 1
fi

if [ -z "$DECISIONS_QUEUE_URL" ] || [ "$DECISIONS_QUEUE_URL" = "None" ]; then
  echo "Missing DECISIONS_QUEUE_URL. Set DECISIONS_QUEUE_URL or DECISIONS_QUEUE_NAME to resolve it." >&2
  exit 1
fi

echo "Using queues:"
echo "  transactions=$QUEUE_URL"
echo "  decisions=$DECISIONS_QUEUE_URL"

kill_pods() {
  sleep "$START_DELAY_SECS"
  i=1
  while [ "$i" -le "$KILL_COUNT" ]; do
    pod=$(kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" \
      -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    if [ -z "$pod" ]; then
      echo "No pod found for label $APP_LABEL in namespace $NAMESPACE."
      break
    fi
    echo "Deleting pod $pod"
    kubectl delete pod "$pod" -n "$NAMESPACE" --wait=false >/dev/null
    i=$((i + 1))
    sleep "$KILL_INTERVAL_SECS"
  done
}

drain_decisions() {
  expected="$1"
  count=0
  deadline=$(( $(date +%s) + DECISION_TIMEOUT_SECS ))

  while [ "$(date +%s)" -lt "$deadline" ]; do
    receipts=$(${AWS_CMD} sqs receive-message \
      --queue-url "$DECISIONS_QUEUE_URL" \
      --max-number-of-messages 10 \
      --wait-time-seconds 2 \
      --query 'Messages[].ReceiptHandle' \
      --output text \
      ${AWS_ARGS})

    if [ -z "$receipts" ] || [ "$receipts" = "None" ]; then
      continue
    fi

    for receipt in $receipts; do
      ${AWS_CMD} sqs delete-message \
        --queue-url "$DECISIONS_QUEUE_URL" \
        --receipt-handle "$receipt" \
        ${AWS_ARGS} >/dev/null
      count=$((count + 1))
    done

    if [ "$count" -ge "$expected" ]; then
      break
    fi
  done

  echo "Received $count decision messages."
  if [ "$count" -lt "$expected" ]; then
    echo "Timed out waiting for decisions (expected $expected)." >&2
    exit 1
  fi
}

drain_existing_decisions() {
  count=0
  deadline=$(( $(date +%s) + 30 ))

  while [ "$(date +%s)" -lt "$deadline" ]; do
    receipts=$(${AWS_CMD} sqs receive-message \
      --queue-url "$DECISIONS_QUEUE_URL" \
      --max-number-of-messages 10 \
      --wait-time-seconds 1 \
      --query 'Messages[].ReceiptHandle' \
      --output text \
      ${AWS_ARGS})

    if [ -z "$receipts" ] || [ "$receipts" = "None" ]; then
      break
    fi

    for receipt in $receipts; do
      ${AWS_CMD} sqs delete-message \
        --queue-url "$DECISIONS_QUEUE_URL" \
        --receipt-handle "$receipt" \
        ${AWS_ARGS} >/dev/null
      count=$((count + 1))
    done
  done

  echo "Drained $count existing decision messages."
}

echo "Starting resilience test:"
echo "  namespace=$NAMESPACE label=$APP_LABEL"
echo "  total_messages=$TOTAL_MESSAGES kill_count=$KILL_COUNT"
echo "  queues: $QUEUE_NAME -> $DECISIONS_QUEUE_NAME"

if [ "$DRAIN_BEFORE" = "true" ]; then
  echo "Draining decision queue before test."
  drain_existing_decisions
fi

kill_pods &
KILL_PID=$!

i=1
while [ "$i" -le "$TOTAL_MESSAGES" ]; do
  tx_id="${TX_ID_PREFIX}${i}"
  payload=$(TX_ID="$tx_id" ./gradlew -q encodeSqsMessage)
  ${AWS_CMD} sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$payload" \
    ${AWS_ARGS} >/dev/null
  i=$((i + 1))
done

wait "$KILL_PID"

drain_decisions "$TOTAL_MESSAGES"

echo "Resilience test completed successfully."
