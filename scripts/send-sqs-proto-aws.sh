#!/usr/bin/env sh
set -e

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required to send messages to AWS SQS." >&2
  exit 1
fi

REGION=${AWS_REGION:-${REGION:-us-east-1}}
QUEUE_URL=${QUEUE_URL:-}
QUEUE_NAME=${QUEUE_NAME:-fintech-transactions}

BASE64_PAYLOAD=$(./gradlew -q encodeSqsMessage)

if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL=$(aws sqs get-queue-url \
    --queue-name "$QUEUE_NAME" \
    --query 'QueueUrl' \
    --output text \
    --region "$REGION")
fi

aws sqs send-message \
  --queue-url "$QUEUE_URL" \
  --message-body "$BASE64_PAYLOAD" \
  --region "$REGION"
