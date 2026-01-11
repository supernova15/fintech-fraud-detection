#!/usr/bin/env sh
set -e

QUEUE_URL=${QUEUE_URL:-}
QUEUE_NAME=${QUEUE_NAME:-fintech-transactions}
REGION=${REGION:-us-east-1}

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

BASE64_PAYLOAD=$(./gradlew -q encodeSqsMessage)

if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL=$(${AWS_CMD} sqs get-queue-url \
    --queue-name "$QUEUE_NAME" \
    --query 'QueueUrl' \
    --output text \
    --region "$REGION")
fi

${AWS_CMD} sqs send-message \
  --queue-url "$QUEUE_URL" \
  --message-body "$BASE64_PAYLOAD" \
  --region "$REGION"
