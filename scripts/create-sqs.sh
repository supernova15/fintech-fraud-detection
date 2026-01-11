aws sqs create-queue --queue-name fintech-transactions --region "$AWS_REGION" >/dev/null
aws sqs create-queue --queue-name fintech-decisions --region "$AWS_REGION" >/dev/null

TXN_Q_URL="$(aws sqs get-queue-url --queue-name fintech-transactions --region "$AWS_REGION" --query QueueUrl --output text)"
DEC_Q_URL="$(aws sqs get-queue-url --queue-name fintech-decisions --region "$AWS_REGION" --query QueueUrl --output text)"

TXN_Q_ARN="$(aws sqs get-queue-attributes --queue-url "$TXN_Q_URL" --attribute-names QueueArn --region "$AWS_REGION" --query Attributes.QueueArn --output text)"
DEC_Q_ARN="$(aws sqs get-queue-attributes --queue-url "$DEC_Q_URL" --attribute-names QueueArn --region "$AWS_REGION" --query Attributes.QueueArn --output text)"
