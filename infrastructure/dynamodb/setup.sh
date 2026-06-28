#!/bin/bash
set -e

ENDPOINT="http://dynamodb-local:8000"

echo "=== Initializing DynamoDB tables ==="

# PaymentEventStore (Event Sourcing)
aws dynamodb create-table \
  --endpoint-url $ENDPOINT \
  --table-name PaymentEventStore \
  --attribute-definitions \
    AttributeName=transactionId,AttributeType=S \
    AttributeName=timestamp,AttributeType=S \
  --key-schema \
    AttributeName=transactionId,KeyType=HASH \
    AttributeName=timestamp,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --stream-specification StreamEnabled=true,StreamViewType=NEW_AND_OLD_IMAGES \
  2>/dev/null || echo "PaymentEventStore already exists"

# PaymentTransactions (CQRS read model)
aws dynamodb create-table \
  --endpoint-url $ENDPOINT \
  --table-name PaymentTransactions \
  --attribute-definitions \
    AttributeName=transactionId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=providerId,AttributeType=S \
    AttributeName=status,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema \
    AttributeName=transactionId,KeyType=HASH \
    AttributeName=createdAt,KeyType=RANGE \
  --global-secondary-indexes \
    '[
      {
        "IndexName": "userId-index",
        "KeySchema": [{"AttributeName":"userId","KeyType":"HASH"},{"AttributeName":"createdAt","KeyType":"RANGE"}],
        "Projection": {"ProjectionType":"ALL"}
      },
      {
        "IndexName": "providerId-index",
        "KeySchema": [{"AttributeName":"providerId","KeyType":"HASH"},{"AttributeName":"createdAt","KeyType":"RANGE"}],
        "Projection": {"ProjectionType":"ALL"}
      },
      {
        "IndexName": "status-index",
        "KeySchema": [{"AttributeName":"status","KeyType":"HASH"},{"AttributeName":"createdAt","KeyType":"RANGE"}],
        "Projection": {"ProjectionType":"ALL"}
      }
    ]' \
  --billing-mode PAY_PER_REQUEST \
  --stream-specification StreamEnabled=true,StreamViewType=NEW_AND_OLD_IMAGES \
  2>/dev/null || echo "PaymentTransactions already exists"

# FraudHistory
aws dynamodb create-table \
  --endpoint-url $ENDPOINT \
  --table-name FraudHistory \
  --attribute-definitions \
    AttributeName=transactionId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=timestamp,AttributeType=N \
  --key-schema \
    AttributeName=transactionId,KeyType=HASH \
  --global-secondary-indexes \
    '[
      {
        "IndexName": "userId-timestamp-index",
        "KeySchema": [{"AttributeName":"userId","KeyType":"HASH"},{"AttributeName":"timestamp","KeyType":"RANGE"}],
        "Projection": {"ProjectionType":"ALL"}
      }
    ]' \
  --billing-mode PAY_PER_REQUEST \
  2>/dev/null || echo "FraudHistory already exists"

# FraudBlacklist
aws dynamodb create-table \
  --endpoint-url $ENDPOINT \
  --table-name FraudBlacklist \
  --attribute-definitions \
    AttributeName=userId,AttributeType=S \
  --key-schema \
    AttributeName=userId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  2>/dev/null || echo "FraudBlacklist already exists"

echo "=== DynamoDB initialization complete ==="
