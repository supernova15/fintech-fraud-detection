#!/usr/bin/env sh
set -e

QUEUE_URL=${QUEUE_URL:-}
QUEUE_NAME=${QUEUE_NAME:-fintech-transactions}
REGION=${REGION:-${AWS_REGION:-us-east-1}}

if command -v aws >/dev/null 2>&1; then
  AWS_CMD="aws"
else
  echo "Missing aws CLI" >&2
  exit 1
fi

AWS_ARGS="--region $REGION"
if [ -n "${AWS_PROFILE:-}" ]; then
  AWS_ARGS="$AWS_ARGS --profile $AWS_PROFILE"
fi

BASE64_PAYLOAD=$(./gradlew -q encodeSqsMessage)

if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL=$(${AWS_CMD} sqs get-queue-url \
    --queue-name "$QUEUE_NAME" \
    --query 'QueueUrl' \
    --output text \
    ${AWS_ARGS})
fi

${AWS_CMD} sqs send-message \
  --queue-url "$QUEUE_URL" \
  --message-body "$BASE64_PAYLOAD" \
  ${AWS_ARGS}
