# Production testing

This guide documents how to send test SQS messages and query recent outbox
records in a production-like AWS environment.

## Send transactions to SQS

Use `scripts/send-sqs-aws.sh` to send one or more transaction requests to the
transactions queue.

Single message:
```
REGION=us-east-1 QUEUE_NAME=fintech-transactions ./scripts/send-sqs-aws.sh
```

Sequential transaction IDs (txn-1000 to txn-1100):
```
REGION=us-east-1 QUEUE_NAME=fintech-transactions \
TX_ID_START=1000 TX_ID_END=1100 TX_ID_PREFIX=txn- \
./scripts/send-sqs-aws.sh
```

Optional env vars:
- `QUEUE_URL`: skip lookup by queue name.
- `QUEUE_NAME`: defaults to `fintech-transactions`.
- `REGION` or `AWS_REGION`: defaults to `us-east-1`.
- `AWS_PROFILE`: named AWS CLI profile.
- `TX_ID`, `ACCOUNT_ID`, `AMOUNT`, `MERCHANT`, `CURRENCY`, `TIMESTAMP`: override
  payload fields for single sends.
- `TX_ID_START`, `TX_ID_END`, `TX_ID_PREFIX`: send a range of IDs when set.

## Query recent outbox records

Use `scripts/query-dynamodb-recent.sh` to query the outbox GSI for records
created in the last N minutes for all statuses.

Default (last 10 minutes):
```
REGION=us-east-1 ./scripts/query-dynamodb-recent.sh
```

Custom window and statuses:
```
REGION=us-east-1 MINUTES=30 STATUSES=PENDING,FAILED \
./scripts/query-dynamodb-recent.sh
```

Optional env vars:
- `TABLE_NAME`: defaults to `fintech-outbox`.
- `INDEX_NAME`: defaults to `status-index`.
- `MINUTES`: defaults to `10`.
- `STATUSES`: comma-separated list of statuses.
- `OUTPUT`: AWS CLI output format, default `json`.
- `REGION` or `AWS_REGION`, `AWS_PROFILE`: AWS CLI settings.

## Production batch test

Use `scripts/production-testing.sh` to drain both queues, send a batch of messages, wait, and then
report decision queue counts.

Example (100 messages, 1 minute wait):
```
REGION=us-east-1 QUEUE_NAME=fintech-transactions \
DECISIONS_QUEUE_NAME=fintech-decisions \
TOTAL_MESSAGES=100 SLEEP_SECS=60 \
./scripts/production-testing.sh
```

Optional env vars:
- `QUEUE_URL`, `DECISIONS_QUEUE_URL`: skip queue name lookups.
- `QUEUE_NAME`, `DECISIONS_QUEUE_NAME`: defaults as above.
- `TOTAL_MESSAGES`: defaults to `100`.
- `SLEEP_SECS`: defaults to `60`.
- `TX_ID_PREFIX`: defaults to `prod-test-`.
- `CONFIGMAP_FILE`: optional source for default queue URLs and table name.
- `REGION` or `AWS_REGION`, `AWS_PROFILE`: AWS CLI settings.
