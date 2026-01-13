# Local Testing (SQS + DynamoDB Outbox)

## Automated integration test (Testcontainers)
1) Ensure Docker is running.
2) Run the integration test:

```sh
RUN_LOCALSTACK_TESTS=true ./gradlew test --tests org.fintech.outbox.SqsOutboxIntegrationTest
```

## Manual LocalStack validation
1) Start LocalStack and the app:

```sh
docker compose -f docker-compose.localstack.yml up -d
./gradlew bootRun --args='--spring.profiles.active=localstack'
```

2) Send a base64-encoded `TransactionRequest` message:

```sh
./scripts/send-sqs-proto.sh
```

Optional overrides: `TX_ID`, `ACCOUNT_ID`, `AMOUNT`, `MERCHANT`, `CURRENCY`, `TIMESTAMP`.

3) Confirm the decision log in the app output:

```
event=sqs_decision transaction_id=tx-123 decision=APPROVE reason=LOW_RISK_AMOUNT risk_score=0.1 message_id=...
```

4) Read the decision message from the queue (optional):

```sh
DECISIONS_QUEUE_URL=$(docker compose -f docker-compose.localstack.yml exec -T localstack \
  awslocal sqs get-queue-url --queue-name fintech-decisions --query 'QueueUrl' --output text)
docker compose -f docker-compose.localstack.yml exec -T localstack \
  awslocal sqs receive-message --queue-url "$DECISIONS_QUEUE_URL" --max-number-of-messages 1
```

5) Inspect the outbox table:

```sh
docker compose -f docker-compose.localstack.yml exec -T localstack \
  awslocal dynamodb scan --table-name fintech-outbox
```

6) Query by status (PENDING or PUBLISHED):

```sh
docker compose -f docker-compose.localstack.yml exec -T localstack \
  awslocal dynamodb query \
  --table-name fintech-outbox \
  --index-name status-index \
  --key-condition-expression "status = :s" \
  --expression-attribute-values '{":s":{"S":"PENDING"}}'
```

Replace `PENDING` with `PUBLISHED` to confirm the publisher updated the record.

## Metrics check (LocalStack profile only)
With the LocalStack profile, Actuator exposes metrics at:

```sh
curl -s http://localhost:8080/actuator/metrics | head -n 20
curl -s http://localhost:8080/actuator/metrics/sqs.process.success
```
