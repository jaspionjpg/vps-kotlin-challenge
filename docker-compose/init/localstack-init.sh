#!/bin/bash
set -e

echo "Initializing LocalStack resources..."

# Create SNS topic for transaction notifications
awslocal sns create-topic --name credit-transactions

# Create SQS queue for processing notifications
awslocal sqs create-queue --name credit-notifications-queue

# Subscribe SQS queue to SNS topic
awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:credit-transactions \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:credit-notifications-queue

echo "LocalStack initialization complete."
