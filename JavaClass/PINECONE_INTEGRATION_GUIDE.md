# Pinecone Integration Setup Guide

## Overview

This guide provides complete setup instructions for integrating Pinecone vector database with your chatbot application. The integration includes:

- **Automatic index creation and management**
- **User namespace isolation** - Each user's data is stored in separate namespaces
- **Document chunking** - Large documents are split into optimal chunks for better retrieval
- **Keyword enhancement** - User queries are enhanced with extracted keywords
- **Top-K similarity search** - Retrieve the most relevant context chunks
- **Detailed source tracking** - Track which chunks contributed to responses

## Prerequisites

1. **Pinecone Account**: Sign up at [https://www.pinecone.io/](https://www.pinecone.io/)
2. **OpenAI API Key**: Required for embeddings generation
3. **Java 17+** and **Spring Boot 3.2+**

## Configuration Steps

### 1. Environment Variables

Set these environment variables in your system or Docker configuration:

```bash
# Required Pinecone Configuration
export PINECONE_API_KEY="your-pinecone-api-key-here"
export PINECONE_API_URL="https://your-index-id.svc.environment.pinecone.io"
export PINECONE_ENVIRONMENT="us-east-1-aws"  # or your preferred region
export PINECONE_PROJECT_ID="your-project-id"

# Required OpenAI Configuration
export OPENAI_API_KEY="your-openai-api-key-here"
```

### 2. Pinecone Setup

#### Getting Your Pinecone Configuration:

1. **Login to Pinecone Console**: Visit [https://app.pinecone.io/](https://app.pinecone.io/)

2. **Get API Key**: 
   - Go to "API Keys" section
   - Copy your API key

3. **Create or Get Index Details**:
   - The application will automatically create an index named `chatbot-poc`
   - If you want to use an existing index, update `pinecone.index-name` in `application.properties`

4. **Get Index URL**:
   - After index creation, get the index URL from the Pinecone console
   - Format: `https://your-index-id.svc.environment.pinecone.io`

### 3. Application Configuration

The application is configured via `src/main/resources/application.properties`:

```properties
# Pinecone Configuration
pinecone.api-key=${PINECONE_API_KEY:your-pinecone-key}
pinecone.api-url=${PINECONE_API_URL:your-pinecone-url}
pinecone.index-name=chatbot-poc
pinecone.environment=${PINECONE_ENVIRONMENT:us-east-1-aws}
pinecone.project-id=${PINECONE_PROJECT_ID:your-project-id}

# OpenAI Configuration
openai.api-key=${OPENAI_API_KEY:your-openai-key}
openai.api-url=https://api.openai.com/v1
```

## Architecture Overview

### User Namespace Isolation

- **Namespace Strategy**: Each user gets their own namespace in Pinecone using their `userId`
- **Data Isolation**: Users can only access their own knowledge bases
- **Security**: API key validation ensures proper user identification

### Document Processing Flow

1. **File Upload** → `KnowledgeController.importFile()`
2. **Text Extraction** → `FileService.extractText()`
3. **Document Chunking** → `PineconeService.splitTextIntoChunks()`
4. **Embedding Generation** → `OpenAiService.createEmbedding()`
5. **Vector Storage** → `PineconeService.upsertDocumentChunks()`

### Query Processing Flow

1. **User Query** → `ChatController.query()`
2. **Keyword Extraction** → `ChatController.extractKeywords()`
3. **Query Embedding** → `OpenAiService.createEmbedding()`
4. **Vector Search** → `PineconeService.queryVectors()`
5. **Context Assembly** → Combine top-K chunks
6. **AI Response** → `OpenAiService.generateChatResponse()`

## API Usage

### 1. Import Knowledge Base

```bash
POST /api/v1/knowledge/import
Content-Type: application/json

{
  "file": "/path/to/document.pdf",
  "name": "My Document",
  "description": "Important company document"
}

Query Parameters:
- api_key (required): User's API key
- idempotency_key (optional): For safe retries
```

**Response:**
```json
{
  "success": true,
  "data": {
    "knowledgeBaseId": "kb_abc12345",
    "status": "processing",
    "message": "File uploaded successfully. Processing will complete shortly.",
    "estimatedCompletion": "2024-01-15T10:30:00Z"
  }
}
```

### 2. Check Processing Status

```bash
GET /api/v1/knowledge/{knowledgeBaseId}/status?api_key=your_api_key
```

### 3. Query Knowledge Base

```bash
POST /api/v1/chat/query
Content-Type: application/json

{
  "question": "What are the key features of our product?",
  "knowledgeBaseId": "kb_abc12345",
  "sessionId": "optional_session_id"
}

Query Parameters:
- api_key (required): User's API key
- top_k (optional, default: 5): Number of context chunks to retrieve
- score_threshold (optional, default: 0.7): Minimum similarity score
```

**Enhanced Response:**
```json
{
  "success": true,
  "data": {
    "response": "Based on your document, the key features include...",
    "knowledgeBaseId": "kb_abc12345",
    "sessionId": "optional_session_id",
    "sources": [
      {
        "chunkId": "kb_abc12345_chunk_0",
        "documentName": "My Document",
        "chunkIndex": 0,
        "relevanceScore": 0.89
      }
    ],
    "contextChunksUsed": 3,
    "minScore": 0.75,
    "maxScore": 0.89
  }
}
```

## Key Features

### 1. Automatic Index Management
- **Auto-Creation**: Index is created automatically on first startup
- **Health Checks**: Built-in connectivity testing
- **Error Handling**: Comprehensive error handling and logging

### 2. Smart Document Chunking
- **Optimal Chunk Size**: 1000 characters per chunk with 200 character overlap
- **Boundary-Aware**: Breaks at sentence/paragraph boundaries when possible
- **Metadata Rich**: Each chunk includes context information

### 3. Enhanced Query Processing
- **Keyword Extraction**: Automatic keyword identification from user questions
- **Stop Word Filtering**: Removes common words that don't add semantic value
- **Similarity Scoring**: Configurable score thresholds for result quality

### 4. User Isolation
- **Namespace Security**: Each user's data is isolated using userId as namespace
- **Access Control**: API key validation ensures proper authorization
- **Data Privacy**: Users can only access their own knowledge bases

## Monitoring and Health Checks

### Basic Health Check
```bash
GET /api/v1/health
```

### Detailed Health Check (includes Pinecone status)
```bash
GET /api/v1/health/detailed
```

**Response includes:**
- Pinecone connection status
- Index statistics
- Vector count information
- Service health status

## Troubleshooting

### Common Issues

1. **"Pinecone initialization failed"**
   - Check API key validity
   - Verify network connectivity
   - Ensure environment/region is correct

2. **"Index creation failed"**
   - Check Pinecone plan limits
   - Verify project permissions
   - Check environment configuration

3. **"No relevant context found"**
   - Try lowering `score_threshold` parameter
   - Increase `top_k` parameter
   - Check if document was processed successfully

4. **"Knowledge base not ready"**
   - Check processing status endpoint
   - Wait for processing to complete
   - Check async processing logs

### Logs to Monitor

```bash
# Application logs
tail -f logs/application.log | grep -E "(PineconeService|KnowledgeController|ChatController)"

# Key log patterns to watch:
# - "Pinecone connection test successful"
# - "Successfully upserted X chunks"
# - "Retrieved X context chunks"
# - "Processing failed" (errors)
```

## Performance Optimization

### Recommended Settings

- **Chunk Size**: 1000 characters (optimal for most documents)
- **Top-K**: 5-10 chunks (balance between context and performance)
- **Score Threshold**: 0.7 (good balance between relevance and recall)
- **Batch Size**: 10 vectors per upsert (Pinecone limit)

### Scaling Considerations

- **Rate Limiting**: Built-in rate limiting prevents API abuse
- **Async Processing**: Knowledge base processing is asynchronous
- **Connection Pooling**: WebClient handles connection reuse
- **Retry Logic**: Automatic retries for transient failures

## Security Features

1. **API Key Authentication**: All endpoints require valid API keys
2. **User Namespace Isolation**: Complete data separation between users
3. **Input Validation**: Comprehensive validation using Bean Validation
4. **Rate Limiting**: Protection against abuse with configurable limits
5. **Idempotency**: Safe retry mechanisms for critical operations

## Next Steps

After setup:

1. **Test Connection**: Use `/api/v1/health/detailed` to verify Pinecone connectivity
2. **Import Documents**: Use the import endpoint to upload knowledge bases
3. **Query Testing**: Test the chat endpoint with various questions
4. **Monitor Performance**: Watch logs and response times
5. **Tune Parameters**: Adjust chunk size, top-K, and score thresholds based on your use case

For production deployment, consider:
- Setting up monitoring and alerting
- Implementing backup strategies
- Optimizing chunk sizes for your specific content
- Adding more sophisticated keyword extraction
