#!/usr/bin/env sh
set -e

TABLE_NAME=${TABLE_NAME:-fintech-outbox}
INDEX_NAME=${INDEX_NAME:-status-index}
REGION=${REGION:-${AWS_REGION:-us-east-1}}
MINUTES=${MINUTES:-10}
STATUSES=${STATUSES:-PENDING,PUBLISHED,FAILED}
OUTPUT=${OUTPUT:-json}

if command -v aws >/dev/null 2>&1; then
  AWS_CMD="aws"
else
  echo "Missing aws CLI" >&2
  exit 1
fi

AWS_ARGS="--region $REGION --output $OUTPUT"
if [ -n "${AWS_PROFILE:-}" ]; then
  AWS_ARGS="$AWS_ARGS --profile $AWS_PROFILE"
fi

NOW_MS=$(( $(date +%s) * 1000 ))
CUTOFF_MS=$(( NOW_MS - (MINUTES * 60000) ))
STATUSES_LIST=$(printf "%s" "$STATUSES" | tr ',' ' ')

for STATUS in $STATUSES_LIST; do
  echo "Status: $STATUS (created_at >= $CUTOFF_MS)"
  ${AWS_CMD} dynamodb query \
    --table-name "$TABLE_NAME" \
    --index-name "$INDEX_NAME" \
    --key-condition-expression "#st = :status and #ca >= :cutoff" \
    --expression-attribute-names "{\"#st\":\"status\",\"#ca\":\"created_at\"}" \
    --expression-attribute-values "{\":status\":{\"S\":\"$STATUS\"},\":cutoff\":{\"N\":\"$CUTOFF_MS\"}}" \
    ${AWS_ARGS}
done
