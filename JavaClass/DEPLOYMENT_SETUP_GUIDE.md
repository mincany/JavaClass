# Complete Deployment & Setup Guide

This guide covers everything needed to get the chatbot system running in both local development and production environments.

## 🏗️ Infrastructure Requirements

### Required Services

1. **AWS DynamoDB** - User and knowledge base storage
2. **AWS S3** - File storage
3. **AWS SQS** - Message queuing
4. **OpenAI API** - Text processing and embeddings
5. **Pinecone** - Vector database for embeddings

### Optional for Local Development
- **LocalStack** - AWS services simulation
- **DynamoDB Local** - Local DynamoDB instance

## 🔧 Environment Setup

### 1. AWS Services Setup

#### DynamoDB Tables
Create two tables in AWS DynamoDB:

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

#### S3 Bucket
```bash
# Create S3 bucket for file storage
aws s3 mb s3://chatbot-knowledge-files --region us-east-1
```

#### SQS Queue
```bash
# Create SQS queue for knowledge processing
aws sqs create-queue \
    --queue-name knowledge-processing-queue \
    --region us-east-1
```

### 2. External Services Setup

#### OpenAI API
1. Sign up at https://platform.openai.com/
2. Generate API key
3. Set up billing (required for API access)

#### Pinecone Setup
1. Sign up at https://www.pinecone.io/
2. Create a new project
3. Create an index:
   - Name: `chatbot-poc`
   - Dimensions: `1536` (for OpenAI embeddings)
   - Metric: `cosine`
   - Environment: Choose your preferred region

## 📝 Environment Variables

Create a `.env` file or set these environment variables:

### Required Variables
```bash
# OpenAI Configuration
OPENAI_API_KEY=sk-your-openai-api-key-here

# Pinecone Configuration
PINECONE_API_KEY=your-pinecone-api-key
PINECONE_API_URL=https://chatbot-poc-xxxxx.svc.us-east-1-aws.pinecone.io
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_PROJECT_ID=your-project-id

# AWS Configuration
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=us-east-1

# AWS Service Names
AWS_S3_BUCKET_NAME=chatbot-knowledge-files
AWS_SQS_KNOWLEDGE_QUEUE=knowledge-processing-queue
```

### Optional Variables (for custom endpoints)
```bash
# For LocalStack or custom endpoints
AWS_SQS_ENDPOINT=http://localhost:4566
AWS_S3_ENDPOINT=http://localhost:4566
AWS_DYNAMODB_ENDPOINT=http://localhost:8000
```

## 🐳 Local Development with LocalStack

### 1. Install LocalStack
```bash
# Using Docker
docker pull localstack/localstack

# Or using pip
pip install localstack
```

### 2. Start LocalStack
```bash
# Using Docker Compose (recommended)
cat > docker-compose.yml << EOF
version: '3.8'
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,sqs,dynamodb
      - DEBUG=1
      - DATA_DIR=/tmp/localstack/data
    volumes:
      - "./localstack-data:/tmp/localstack"
      - "/var/run/docker.sock:/var/run/docker.sock"
EOF

docker-compose up -d
```

### 3. Setup Local AWS Services
```bash
# Set AWS CLI to use LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Create DynamoDB tables
aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name chatbot-users \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name chatbot-knowledge \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

# Create S3 bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://chatbot-knowledge-files

# Create SQS queue
aws --endpoint-url=http://localhost:4566 sqs create-queue \
    --queue-name knowledge-processing-queue
```

### 4. Local Environment Variables
```bash
# Local development .env file
OPENAI_API_KEY=sk-your-openai-api-key-here
PINECONE_API_KEY=your-pinecone-api-key
PINECONE_API_URL=https://chatbot-poc-xxxxx.svc.us-east-1-aws.pinecone.io
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_PROJECT_ID=your-project-id

# LocalStack endpoints
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
AWS_SQS_ENDPOINT=http://localhost:4566
AWS_S3_BUCKET_NAME=chatbot-knowledge-files
AWS_SQS_KNOWLEDGE_QUEUE=knowledge-processing-queue
```

## 🚀 Application Startup

### 1. Build the Application
```bash
mvn clean package -DskipTests
```

### 2. Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Or using Java directly
java -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

### 3. Verify Application is Running
```bash
# Health check
curl http://localhost:9090/api/v1/health

# Expected response:
{
  "status": "UP",
  "timestamp": "2024-01-01T12:00:00Z",
  "version": "0.0.1-SNAPSHOT",
  "services": {
    "database": "UP",
    "openai": "UP",
    "pinecone": "UP"
  }
}
```

## 🧪 Testing the System

### 1. Create a Test User
```bash
curl -X POST "http://localhost:9090/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'
```

### 2. Upload a Knowledge Base File
```bash
# Create a test file
echo "This is a test document for the knowledge base. It contains important information about our products and services." > test-document.txt

# Upload the file
curl -X POST "http://localhost:9090/api/v1/knowledge/import" \
  -H "Content-Type: application/json" \
  -d '{
    "file": "/path/to/test-document.txt",
    "name": "Test Document",
    "description": "A test document for verification"
  }' \
  -G -d "api_key=your-api-key-from-registration"
```

### 3. Check Processing Status
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/{knowledge_base_id}/status" \
  -G -d "api_key=your-api-key"
```

### 4. Test Chat with Knowledge Base
```bash
curl -X POST "http://localhost:9090/api/v1/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What information do you have about our products?",
    "conversation_id": "test-conversation"
  }' \
  -G -d "api_key=your-api-key"
```

## 🔍 Troubleshooting

### Common Issues

#### 1. Application Won't Start
- Check all environment variables are set
- Verify AWS credentials are valid
- Ensure all required services are running

#### 2. SQS Messages Not Processing
- Check SQS queue exists and is accessible
- Verify AWS credentials have SQS permissions
- Check application logs for SQS listener errors

#### 3. S3 Upload Failures
- Verify S3 bucket exists and is accessible
- Check AWS credentials have S3 permissions
- Ensure bucket name is unique globally

#### 4. Pinecone Connection Issues
- Verify Pinecone API key is correct
- Check Pinecone index exists with correct dimensions
- Ensure Pinecone URL matches your index

### Debugging Commands

```bash
# Check LocalStack services
curl http://localhost:4566/health

# List SQS queues
aws --endpoint-url=http://localhost:4566 sqs list-queues

# List S3 buckets
aws --endpoint-url=http://localhost:4566 s3 ls

# Check DynamoDB tables
aws --endpoint-url=http://localhost:4566 dynamodb list-tables

# View application logs
tail -f logs/application.log
```

## 📊 Monitoring

### Application Metrics
- Health endpoint: `GET /api/v1/health`
- Processing status: Monitor knowledge base status in database
- SQS queue depth: Monitor message count in AWS Console

### Log Files
- Application logs: `logs/application.log`
- Error logs: `logs/error.log`
- SQS processing logs: Search for "KnowledgeProcessingListener" in logs

## 🔒 Security Considerations

### Production Deployment
1. **Use IAM roles** instead of access keys when possible
2. **Enable S3 bucket encryption**
3. **Use VPC endpoints** for AWS services
4. **Enable CloudTrail** for audit logging
5. **Use secrets manager** for sensitive configuration
6. **Enable HTTPS** with proper SSL certificates

### Environment-Specific Configuration
- **Development**: Use LocalStack with test credentials
- **Staging**: Use separate AWS account with limited permissions
- **Production**: Use production AWS account with proper IAM policies

## 📋 Deployment Checklist

- [ ] AWS services created (DynamoDB, S3, SQS)
- [ ] OpenAI API key obtained and configured
- [ ] Pinecone index created and configured
- [ ] Environment variables set
- [ ] Application builds successfully
- [ ] Health check passes
- [ ] Test user registration works
- [ ] File upload and processing works
- [ ] Chat functionality works
- [ ] Monitoring and logging configured
- [ ] Security measures implemented

## 🆘 Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review application logs
3. Verify all environment variables
4. Test individual components (AWS services, OpenAI, Pinecone)
5. Check network connectivity and firewall settings
