# SQS Integration Guide

This document explains how the SQS integration works in the Knowledge Base processing system.

## Architecture Overview

The system now uses AWS SQS for asynchronous processing of knowledge base files:

1. **API Request** → **S3 Upload** → **SQS Message** → **Background Processing** → **Pinecone Storage**

## Components

### 1. KnowledgeController
- Receives file upload requests
- Uploads files to S3
- Creates database records
- Sends processing messages to SQS
- Returns immediate response to client

### 2. S3Service
- Handles file uploads to S3
- Downloads files from S3 for processing
- Manages S3 bucket operations
- Generates structured S3 keys: `knowledge-bases/{userId}/{knowledgeBaseId}/{filename}`

### 3. SqsService
- Sends messages to SQS queue
- Handles message serialization
- Manages queue creation
- Supports retry logic with exponential backoff

### 4. KnowledgeProcessingListener
- Listens for SQS messages
- Downloads files from S3
- Processes files (text extraction)
- Stores embeddings in Pinecone
- Updates database status
- Handles errors and retries

## Message Flow

### 1. File Upload Flow
```
POST /api/v1/knowledge/import
├── Validate API key and file
├── Create KnowledgeBase record (status: "uploading")
├── Upload file to S3
├── Update status to "pending"
├── Send SQS message
└── Return 202 Accepted
```

### 2. Processing Flow
```
SQS Message Received
├── Update status to "processing"
├── Download file from S3
├── Extract text content
├── Generate embeddings (OpenAI)
├── Store in Pinecone
├── Update status to "completed"
└── Clean up temporary files
```

### 3. Error Handling
```
Processing Error
├── Check retry count
├── If < MAX_RETRIES (3):
│   ├── Increment retry count
│   ├── Send delayed message (exponential backoff)
│   └── Update status to "retrying"
└── Else:
    └── Update status to "failed"
```

## Configuration

### Environment Variables
```properties
# AWS Configuration
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_S3_BUCKET_NAME=chatbot-knowledge-files
AWS_SQS_KNOWLEDGE_QUEUE=knowledge-processing-queue

# Optional (for LocalStack)
AWS_SQS_ENDPOINT=http://localhost:4566
```

### Application Properties
```properties
# AWS S3 Configuration
aws.s3.bucket-name=${AWS_S3_BUCKET_NAME:chatbot-knowledge-files}
aws.s3.region=${AWS_S3_REGION:us-east-1}

# AWS SQS Configuration
aws.sqs.knowledge-processing-queue=${AWS_SQS_KNOWLEDGE_QUEUE:knowledge-processing-queue}
aws.sqs.region=${AWS_SQS_REGION:us-east-1}
aws.sqs.endpoint=${AWS_SQS_ENDPOINT:}

# Spring Cloud AWS SQS
spring.cloud.aws.region.static=${aws.region}
spring.cloud.aws.credentials.access-key=${AWS_ACCESS_KEY_ID:}
spring.cloud.aws.credentials.secret-key=${AWS_SECRET_ACCESS_KEY:}
```

## Status Tracking

The system tracks processing status through the following states:

- **uploading**: File is being uploaded to S3
- **pending**: File uploaded, waiting for processing
- **processing**: File is being processed
- **retrying**: Processing failed, retrying
- **completed**: Processing completed successfully
- **failed**: Processing failed after max retries

## API Usage

### Upload File
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import" \
  -H "Content-Type: application/json" \
  -d '{
    "file": "/path/to/document.pdf",
    "name": "My Document",
    "description": "Important document for knowledge base"
  }' \
  -G -d "api_key=your-api-key"
```

### Check Status
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/{knowledge_base_id}/status" \
  -G -d "api_key=your-api-key"
```

## Benefits

1. **Scalability**: SQS handles message queuing and load balancing
2. **Reliability**: Built-in retry mechanism with exponential backoff
3. **Durability**: Files stored in S3, messages in SQS
4. **Monitoring**: CloudWatch integration for SQS metrics
5. **Cost-effective**: Pay per use, no idle resources

## Local Development

For local development, you can use LocalStack to simulate AWS services:

```bash
# Start LocalStack
docker run -d -p 4566:4566 localstack/localstack

# Set environment variables
export AWS_SQS_ENDPOINT=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

## Monitoring

Monitor the system through:

1. **Application logs**: Processing status and errors
2. **SQS metrics**: Message count, processing time
3. **S3 metrics**: Upload/download statistics
4. **Database**: Knowledge base status tracking

## Error Scenarios

The system handles various error scenarios:

1. **S3 upload failure**: Returns error immediately
2. **SQS send failure**: Returns error immediately
3. **Processing failure**: Retries with exponential backoff
4. **Pinecone failure**: Retries with exponential backoff
5. **File corruption**: Marks as failed after retries
