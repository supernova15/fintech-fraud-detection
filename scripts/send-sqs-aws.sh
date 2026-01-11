#!/usr/bin/env sh
set -e

QUEUE_URL=${QUEUE_URL:-}
QUEUE_NAME=${QUEUE_NAME:-fintech-transactions}
REGION=${REGION:-${AWS_REGION:-us-east-1}}
TX_ID_START=${TX_ID_START:-}
TX_ID_END=${TX_ID_END:-}
TX_ID_PREFIX=${TX_ID_PREFIX:-txn-}

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

if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL=$(${AWS_CMD} sqs get-queue-url \
    --queue-name "$QUEUE_NAME" \
    --query 'QueueUrl' \
    --output text \
    ${AWS_ARGS})
fi

if [ -n "$TX_ID_START" ] || [ -n "$TX_ID_END" ]; then
  if [ -z "$TX_ID_START" ] || [ -z "$TX_ID_END" ]; then
    echo "Set both TX_ID_START and TX_ID_END for ranged sends." >&2
    exit 1
  fi
  case "$TX_ID_START" in
    *[!0-9]*|"") echo "TX_ID_START must be a number." >&2; exit 1 ;;
  esac
  case "$TX_ID_END" in
    *[!0-9]*|"") echo "TX_ID_END must be a number." >&2; exit 1 ;;
  esac
  if [ "$TX_ID_START" -gt "$TX_ID_END" ]; then
    echo "TX_ID_START must be <= TX_ID_END." >&2
    exit 1
  fi
  for i in $(seq "$TX_ID_START" "$TX_ID_END"); do
    TX_ID="${TX_ID_PREFIX}${i}"
    BASE64_PAYLOAD=$(TX_ID="$TX_ID" ./gradlew -q encodeSqsMessage)
    ${AWS_CMD} sqs send-message \
      --queue-url "$QUEUE_URL" \
      --message-body "$BASE64_PAYLOAD" \
      ${AWS_ARGS}
  done
else
  BASE64_PAYLOAD=$(./gradlew -q encodeSqsMessage)
  ${AWS_CMD} sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$BASE64_PAYLOAD" \
    ${AWS_ARGS}
fi
