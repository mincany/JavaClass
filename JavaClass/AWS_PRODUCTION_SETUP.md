# AWS Production Setup Guide

This guide will help you set up the chatbot system with real AWS services instead of LocalStack.

## 🔧 Configuration Approach

The application uses **environment variables** for configuration, not `.env` files. The `application.properties` file references environment variables using Spring's `${VARIABLE_NAME:default}` syntax.

## 📋 Required Environment Variables

Set these environment variables in your system before running the application:

### 1. AWS Credentials & Region
```bash
export AWS_ACCESS_KEY_ID="your-aws-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-aws-secret-access-key"
export AWS_REGION="us-east-1"
```

### 2. OpenAI Configuration
```bash
export OPENAI_API_KEY="sk-your-openai-api-key"
```

### 3. Pinecone Configuration
```bash
export PINECONE_API_KEY="your-pinecone-api-key"
export PINECONE_API_URL="https://chatbot-poc-xxxxx.svc.us-east-1-aws.pinecone.io"
export PINECONE_ENVIRONMENT="us-east-1-aws"
export PINECONE_PROJECT_ID="your-project-id"
```

### 4. AWS Service Names (Optional - uses defaults if not set)
```bash
export AWS_S3_BUCKET_NAME="your-unique-bucket-name"
export AWS_SQS_KNOWLEDGE_QUEUE="knowledge-processing-queue"
export AWS_DYNAMODB_USERS_TABLE="chatbot-users"
export AWS_DYNAMODB_KNOWLEDGE_TABLE="chatbot-knowledge"
```

## 🏗️ AWS Infrastructure Setup

### Step 1: Create AWS Resources

#### 1.1 Create DynamoDB Tables
```bash
# Users table
aws dynamodb create-table \
    --table-name chatbot-users \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

# Knowledge base table  
aws dynamodb create-table \
    --table-name chatbot-knowledge \
    --attribute-definitions \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1
```

#### 1.2 Create S3 Bucket
```bash
# Create bucket (must be globally unique)
aws s3 mb s3://your-unique-chatbot-knowledge-files --region us-east-1

# Enable versioning (recommended)
aws s3api put-bucket-versioning \
    --bucket your-unique-chatbot-knowledge-files \
    --versioning-configuration Status=Enabled
```

#### 1.3 Create SQS Queue
```bash
# Create standard queue
aws sqs create-queue \
    --queue-name knowledge-processing-queue \
    --region us-east-1 \
    --attributes '{
        "VisibilityTimeoutSeconds": "300",
        "MessageRetentionPeriod": "1209600",
        "ReceiveMessageWaitTimeSeconds": "20"
    }'
```

### Step 2: Set Up IAM Permissions

Create an IAM user with the following permissions:

#### 2.1 Create IAM Policy
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "dynamodb:GetItem",
                "dynamodb:PutItem",
                "dynamodb:UpdateItem",
                "dynamodb:DeleteItem",
                "dynamodb:Query",
                "dynamodb:Scan"
            ],
            "Resource": [
                "arn:aws:dynamodb:us-east-1:*:table/chatbot-users",
                "arn:aws:dynamodb:us-east-1:*:table/chatbot-knowledge"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::your-unique-chatbot-knowledge-files",
                "arn:aws:s3:::your-unique-chatbot-knowledge-files/*"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "sqs:SendMessage",
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "sqs:GetQueueAttributes",
                "sqs:GetQueueUrl"
            ],
            "Resource": "arn:aws:sqs:us-east-1:*:knowledge-processing-queue"
        }
    ]
}
```

#### 2.2 Create IAM User
```bash
# Create user
aws iam create-user --user-name chatbot-service-user

# Attach policy (save the policy above as chatbot-policy.json)
aws iam put-user-policy \
    --user-name chatbot-service-user \
    --policy-name ChatbotServicePolicy \
    --policy-document file://chatbot-policy.json

# Create access keys
aws iam create-access-key --user-name chatbot-service-user
```

## 🚀 Running the Application

### Step 1: Set Environment Variables

Create a script to set all environment variables:

```bash
#!/bin/bash
# save as set-env.sh

# AWS Configuration
export AWS_ACCESS_KEY_ID="AKIA..."  # Your access key
export AWS_SECRET_ACCESS_KEY="..."  # Your secret key
export AWS_REGION="us-east-1"

# OpenAI
export OPENAI_API_KEY="sk-..."

# Pinecone
export PINECONE_API_KEY="..."
export PINECONE_API_URL="https://chatbot-poc-xxxxx.svc.us-east-1-aws.pinecone.io"
export PINECONE_ENVIRONMENT="us-east-1-aws"
export PINECONE_PROJECT_ID="..."

# AWS Services (optional - uses defaults)
export AWS_S3_BUCKET_NAME="your-unique-chatbot-knowledge-files"
export AWS_SQS_KNOWLEDGE_QUEUE="knowledge-processing-queue"

echo "Environment variables set successfully!"
```

### Step 2: Run the Application

```bash
# Set environment variables
source set-env.sh

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run
```

### Step 3: Verify Setup

```bash
# Check health
curl http://localhost:9090/api/v1/health

# Should return:
{
  "status": "UP",
  "timestamp": "...",
  "version": "0.0.1-SNAPSHOT",
  "services": {
    "database": "UP",
    "openai": "UP", 
    "pinecone": "UP"
  }
}
```

## 🔍 Troubleshooting

### Common Issues

#### 1. AWS Credentials Not Found
```
Error: Unable to load AWS credentials from any provider in the chain
```
**Solution**: Ensure `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` are set

#### 2. DynamoDB Access Denied
```
Error: User is not authorized to perform: dynamodb:GetItem
```
**Solution**: Check IAM permissions for DynamoDB tables

#### 3. S3 Bucket Access Denied
```
Error: Access Denied (Service: Amazon S3)
```
**Solution**: 
- Ensure bucket name is unique globally
- Check IAM permissions for S3
- Verify bucket exists in the correct region

#### 4. SQS Queue Not Found
```
Error: AWS.SimpleQueueService.NonExistentQueue
```
**Solution**: 
- Ensure queue exists in the correct region
- Check queue name matches environment variable

### Debug Commands

```bash
# Test AWS CLI access
aws sts get-caller-identity

# List DynamoDB tables
aws dynamodb list-tables --region us-east-1

# List S3 buckets
aws s3 ls

# List SQS queues
aws sqs list-queues --region us-east-1

# Check application logs
tail -f logs/application.log
```

## 🔒 Security Best Practices

### 1. Use IAM Roles (Recommended for EC2/ECS)
Instead of access keys, use IAM roles when running on AWS infrastructure:

```bash
# Remove these from environment
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY

# Application will automatically use instance role
```

### 2. Rotate Access Keys Regularly
```bash
# Create new access key
aws iam create-access-key --user-name chatbot-service-user

# Update environment variables with new key
# Delete old access key
aws iam delete-access-key --user-name chatbot-service-user --access-key-id OLD_KEY_ID
```

### 3. Enable CloudTrail
```bash
aws cloudtrail create-trail \
    --name chatbot-audit-trail \
    --s3-bucket-name your-cloudtrail-bucket
```

## 📊 Monitoring

### CloudWatch Metrics
Monitor these key metrics:
- **DynamoDB**: Read/Write capacity, throttling
- **S3**: Request count, error rate
- **SQS**: Message count, processing time

### Application Logs
Key log patterns to monitor:
- `ERROR` - Application errors
- `KnowledgeProcessingListener` - SQS processing
- `S3Service` - File operations
- `PineconeService` - Vector operations

## 💰 Cost Optimization

### DynamoDB
- Use on-demand billing for variable workloads
- Consider provisioned capacity for predictable workloads

### S3
- Use Standard storage class for active files
- Consider Intelligent Tiering for cost optimization

### SQS
- Standard queues are cheaper than FIFO
- Monitor dead letter queue usage

## 🎯 Production Checklist

- [ ] AWS resources created (DynamoDB, S3, SQS)
- [ ] IAM user created with minimal permissions
- [ ] Access keys generated and secured
- [ ] Environment variables configured
- [ ] OpenAI API key obtained
- [ ] Pinecone index created
- [ ] Application builds successfully
- [ ] Health check passes
- [ ] Test file upload works
- [ ] SQS processing works
- [ ] CloudWatch monitoring enabled
- [ ] CloudTrail logging enabled

You're now ready to run with real AWS services! 🚀
