aws dynamodb create-table \
  --table-name fintech-outbox \
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
  ]' \
  --region "$AWS_REGION"

OUTBOX_T_ARN="$(aws dynamodb describe-table --table-name fintech-outbox --region "$AWS_REGION" --query Table.TableArn --output text)"
