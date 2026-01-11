#!/usr/bin/env sh
set -e

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required to query DynamoDB." >&2
  exit 1
fi

if command -v python3 >/dev/null 2>&1; then
  START_MS=$(python3 - <<'PY'
import time
print(int(time.time() * 1000) - 10 * 60 * 1000)
PY
)
else
  START_MS=$(( $(date +%s) * 1000 - 10 * 60 * 1000 ))
fi

REGION=${AWS_REGION:-${REGION:-us-east-1}}
TABLE_NAME=${TABLE_NAME:-fintech-outbox}
LIMIT=${LIMIT:-50}

aws dynamodb scan \
  --table-name "$TABLE_NAME" \
  --filter-expression "created_at >= :t" \
  --expression-attribute-values "{\":t\":{\"N\":\"$START_MS\"}}" \
  --limit "$LIMIT" \
  --region "$REGION"
