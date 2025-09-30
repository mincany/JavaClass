# Pinecone Integration Test Guide

## Quick Test Commands

### 1. Check Health Status
```bash
curl -X GET "http://localhost:9090/api/v1/health/detailed"
```

Expected response should show Pinecone status as "UP".

### 2. Test Knowledge Base Import
```bash
# Create a test file
echo "This is a test document with important information about our product features. The product has advanced AI capabilities, user-friendly interface, and robust security measures." > /tmp/test_document.txt

# Import the knowledge base
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=test_api_key" \
  -H "Content-Type: application/json" \
  -d '{
    "file": "/tmp/test_document.txt",
    "name": "Test Document",
    "description": "Test document for Pinecone integration"
  }'
```

### 3. Check Processing Status
```bash
# Replace {knowledge_base_id} with the ID from step 2
curl -X GET "http://localhost:9090/api/v1/knowledge/{knowledge_base_id}/status?api_key=test_api_key"
```

### 4. Test Chat Query
```bash
# Replace {knowledge_base_id} with the ID from step 2
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=test_api_key&top_k=3&score_threshold=0.5" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the product features?",
    "knowledgeBaseId": "{knowledge_base_id}",
    "sessionId": "test_session_001"
  }'
```

Expected response should include:
- AI-generated answer based on the document content
- Source information showing which chunks were used
- Relevance scores for each chunk
- Context statistics

## Verification Checklist

- [ ] Health endpoint shows Pinecone status as "UP"
- [ ] Document import returns 202 Accepted status
- [ ] Processing completes successfully (status becomes "completed")
- [ ] Chat queries return relevant answers with source information
- [ ] User namespace isolation works (different users can't access each other's data)
- [ ] Vector search returns results with appropriate similarity scores

## Integration Highlights

### 🎯 **User Namespace Isolation**
- Each user's vectors are stored in separate Pinecone namespaces
- Complete data isolation between users
- Secure access control via API keys

### 📝 **Smart Document Chunking**
- Documents are split into 1000-character chunks with 200-character overlap
- Boundary-aware splitting (prefers sentence/paragraph breaks)
- Rich metadata attached to each chunk

### 🔍 **Enhanced Query Processing**
- Automatic keyword extraction from user questions
- Stop word filtering for better semantic search
- Configurable top-K retrieval and similarity thresholds

### 📊 **Detailed Response Metadata**
- Source tracking for transparency
- Similarity scores for each context chunk
- Statistics on context usage

### 🛡️ **Production-Ready Features**
- Comprehensive error handling and logging
- Rate limiting to prevent abuse
- Idempotency for safe retries
- Async processing for better performance
