#!/usr/bin/env sh
set -e
awslocal sqs create-queue --queue-name fintech-transactions
