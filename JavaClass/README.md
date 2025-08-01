# AI Chatbot Backend POC

A Spring Boot application that provides a REST API for an AI-powered chatbot with document ingestion and query capabilities.

## Features

- **User Registration**: Create users with API keys
- **Knowledge Import**: Upload and process text documents (TXT, PDF, DOCX, MD)
- **AI-Powered Chat**: Ask questions about uploaded documents using OpenAI
- **Vector Search**: Uses Pinecone for semantic search of document content

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for local DynamoDB)
- OpenAI API key
- Pinecone API key and index

## Setup Instructions

### 1. Local DynamoDB Setup

```bash
# Start local DynamoDB
docker run -p 8000:8000 amazon/dynamodb-local

# Create tables
aws dynamodb create-table \
  --table-name chatbot-users \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000

aws dynamodb create-table \
  --table-name chatbot-knowledge \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000
```

### 2. Environment Variables

```bash
export OPENAI_API_KEY="your-openai-api-key"
export PINECONE_API_KEY="your-pinecone-api-key"
export PINECONE_API_URL="your-pinecone-index-url"
```

### 3. Run Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:9090`

## API Usage

### 1. Register User

```bash
curl -X POST http://localhost:9090/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

Response:
```json
{
  "userId": "user_12345678",
  "apiKey": "ak_abcdef123456...",
  "email": "test@example.com",
  "message": "Registration successful. Save your API key securely."
}
```

### 2. Upload Document

```bash
curl -X POST http://localhost:9090/api/v1/knowledge/import \
  -F "file=@document.txt" \
  -F "api_key=ak_your_api_key_here" \
  -F "name=My Document" \
  -F "description=A test document"
```

Response:
```json
{
  "knowledge_base_id": "kb_87654321",
  "status": "processed",
  "message": "File processed successfully"
}
```

### 3. Ask Question

```bash
curl -X POST http://localhost:9090/api/v1/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "knowledgeBaseId": "kb_87654321",
    "question": "What is this document about?"
  }' \
  -G -d "api_key=ak_your_api_key_here"
```

Response:
```json
{
  "response": "Based on the document, this appears to be about...",
  "knowledgeBaseId": "kb_87654321"
}
```

## Configuration

Key configuration properties in `application.properties`:

- `server.port=9090` - Server port
- `openai.api-key` - OpenAI API key
- `pinecone.api-key` - Pinecone API key
- `pinecone.api-url` - Pinecone index URL
- `aws.dynamodb.endpoint` - DynamoDB endpoint (local: http://localhost:8000)

## Supported File Types

- Plain text (.txt)
- PDF (.pdf)
- Word documents (.docx)
- Markdown (.md)

Maximum file size: 10MB

## Architecture

- **Spring Boot** - REST API framework
- **DynamoDB** - User and knowledge base metadata storage
- **OpenAI** - Text embeddings and chat completions
- **Pinecone** - Vector database for semantic search
- **Apache Tika** - Document text extraction

## Development Notes

This is a POC implementation with simplified:
- Authentication (API key in request parameters)
- Error handling (basic try-catch blocks)
- File processing (synchronous, no chunking)
- Vector storage (single vector per document)

For production use, consider:
- JWT-based authentication
- Async file processing with queues
- Document chunking for large files
- Enhanced error handling and logging
- Rate limiting and input validation 