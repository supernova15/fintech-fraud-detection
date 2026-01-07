#!/usr/bin/env sh
set -e

TRANSACTIONS_QUEUE=${TRANSACTIONS_QUEUE:-fintech-transactions}
DECISIONS_QUEUE=${DECISIONS_QUEUE:-fintech-decisions}
OUTBOX_TABLE=${OUTBOX_TABLE:-fintech-outbox}

awslocal sqs create-queue --queue-name "$TRANSACTIONS_QUEUE"
awslocal sqs create-queue --queue-name "$DECISIONS_QUEUE"

if ! awslocal dynamodb describe-table --table-name "$OUTBOX_TABLE" >/dev/null 2>&1; then
  awslocal dynamodb create-table \
    --table-name "$OUTBOX_TABLE" \
    --attribute-definitions \
      AttributeName=outbox_id,AttributeType=S \
      AttributeName=status,AttributeType=S \
      AttributeName=created_at,AttributeType=N \
    --key-schema AttributeName=outbox_id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --global-secondary-indexes '[
      {
        "IndexName":"status-index",
        "KeySchema":[
          {"AttributeName":"status","KeyType":"HASH"},
          {"AttributeName":"created_at","KeyType":"RANGE"}
        ],
        "Projection":{"ProjectionType":"ALL"}
      }
    ]'
fi
