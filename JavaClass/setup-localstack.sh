#!/bin/bash

# LocalStack Setup Script for Chatbot Development
# This script sets up LocalStack with all required AWS services

set -e

echo "🚀 Setting up LocalStack for Chatbot Development..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Start LocalStack
echo "📦 Starting LocalStack..."
docker run -d \
    --name chatbot-localstack \
    -p 4566:4566 \
    -e SERVICES=s3,sqs,dynamodb \
    -e DEBUG=1 \
    -e DATA_DIR=/tmp/localstack/data \
    -v "./localstack-data:/tmp/localstack" \
    localstack/localstack:latest

# Wait for LocalStack to be ready
echo "⏳ Waiting for LocalStack to be ready..."
sleep 10

# Set AWS CLI configuration for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

# Function to check if LocalStack is ready
check_localstack() {
    curl -s http://localhost:4566/health > /dev/null
}

# Wait for LocalStack to be fully ready
echo "🔍 Checking LocalStack health..."
for i in {1..30}; do
    if check_localstack; then
        echo "✅ LocalStack is ready!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ LocalStack failed to start properly"
        exit 1
    fi
    echo "⏳ Waiting for LocalStack... ($i/30)"
    sleep 2
done

# Create DynamoDB tables
echo "📊 Creating DynamoDB tables..."

# Users table
aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name chatbot-users \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

# Knowledge base table
aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name chatbot-knowledge \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

echo "✅ DynamoDB tables created successfully"

# Create S3 bucket
echo "🪣 Creating S3 bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://chatbot-knowledge-files --region us-east-1
echo "✅ S3 bucket created successfully"

# Create SQS queue
echo "📬 Creating SQS queue..."
aws --endpoint-url=http://localhost:4566 sqs create-queue \
    --queue-name knowledge-processing-queue \
    --region us-east-1
echo "✅ SQS queue created successfully"

# Verify setup
echo "🔍 Verifying setup..."

# List DynamoDB tables
echo "📊 DynamoDB tables:"
aws --endpoint-url=http://localhost:4566 dynamodb list-tables --region us-east-1

# List S3 buckets
echo "🪣 S3 buckets:"
aws --endpoint-url=http://localhost:4566 s3 ls

# List SQS queues
echo "📬 SQS queues:"
aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1

echo ""
echo "🎉 LocalStack setup completed successfully!"
echo ""
echo "📝 Next steps:"
echo "1. Copy environment-template.txt to .env"
echo "2. Fill in your OpenAI and Pinecone API keys"
echo "3. Uncomment the LocalStack environment variables in .env"
echo "4. Run: mvn spring-boot:run"
echo ""
echo "🛑 To stop LocalStack: docker stop chatbot-localstack"
echo "🗑️  To remove LocalStack: docker rm chatbot-localstack"
