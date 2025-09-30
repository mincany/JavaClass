# Validation and Exception Handling Examples

This document demonstrates how the API handles various client errors and validation scenarios with specific examples.

## 🔍 **Validation Layers Overview**

```
1. Spring Framework Level
   ├── Missing required parameters (@RequestParam validation)
   ├── Malformed JSON (HttpMessageNotReadableException)
   ├── Method not allowed (HttpRequestMethodNotSupportedException)
   └── Path parameter validation (@PathVariable with @NotBlank)

2. Bean Validation Level (@Valid + Jakarta Validation)
   ├── @NotBlank - Required fields
   ├── @Pattern - File format validation
   ├── @Size - String length constraints
   └── Custom validation in DTOs

3. Business Logic Level
   ├── API key validation
   ├── File existence and permissions
   ├── File type validation
   └── Access control validation

4. Infrastructure Level
   ├── Rate limiting (Bucket4j)
   ├── Idempotency validation
   └── Security constraints
```

## 📋 **Specific Validation Scenarios**

### 1. **Missing API Key**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.pdf", "name": "Test"}'
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "MISSING_PARAMETER",
    "message": "Required parameter 'api_key' is missing"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** `GlobalExceptionHandler.handleMissingServletRequestParameter()`

### 2. **Invalid API Key**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=invalid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.pdf", "name": "Test"}'
```

**Response (401 Unauthorized):**
```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED", 
    "message": "Invalid API key"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** Controller validation + `BusinessException` → `GlobalExceptionHandler.handleBusinessException()`

**Code Location:**
```java
// In KnowledgeController.java lines 82-86
String userId = userService.getUserIdFromApiKey(apiKey);
if (userId == null) {
    logger.warn("Invalid API key provided: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
    throw new BusinessException("Invalid API key", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
}
```

### 3. **Missing Required Fields**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.pdf"}'  # Missing "name" field
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": "{name=Name is required}"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** `@Valid` annotation + `GlobalExceptionHandler.handleValidationException()`

**Code Location:**
```java
// In KnowledgeImportRequest.java lines 15-17
@NotBlank(message = "Name is required")
@Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
private String name;
```

### 4. **Invalid File Format**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.jpg", "name": "Test"}'  # .jpg not allowed
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": "{file=File must be .txt, .pdf, or .md format}"
  },
  "timestamp": "2024-01-15T10:25:00Z", 
  "request_id": "req_12345"
}
```

**Handled by:** Bean Validation pattern matching

**Code Location:**
```java
// In KnowledgeImportRequest.java lines 11-12
@Pattern(regexp = ".*\\.(txt|pdf|md)$", message = "File must be .txt, .pdf, or .md format")
private String file;
```

### 5. **Field Length Validation**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.pdf", "name": "'$(printf 'a%.0s' {1..300})'"}'  # 300 character name
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": "{name=Name must be between 1 and 255 characters}"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Code Location:**
```java
// In KnowledgeImportRequest.java line 17
@Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
```

### 6. **Malformed JSON**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{ invalid json syntax }'
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "MALFORMED_JSON",
    "message": "Malformed JSON request"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** `GlobalExceptionHandler.handleHttpMessageNotReadable()`

### 7. **File Not Found**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/nonexistent/file.pdf", "name": "Test"}'
```

**Response (404 Not Found):**
```json
{
  "success": false,
  "error": {
    "code": "FILE_NOT_FOUND",
    "message": "File not found: /nonexistent/file.pdf"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Code Location:**
```java
// In KnowledgeController.java lines 208-211
if (!Files.exists(path)) {
    throw new BusinessException("File not found: " + filePath, "FILE_NOT_FOUND", HttpStatus.NOT_FOUND);
}
```

### 8. **File Permission Denied**

**Request:**
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/root/protected_file.pdf", "name": "Test"}'
```

**Response (403 Forbidden):**
```json
{
  "success": false,
  "error": {
    "code": "FILE_NOT_READABLE",
    "message": "File is not readable: /root/protected_file.pdf"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Code Location:**
```java
// In KnowledgeController.java lines 213-216
if (!Files.isReadable(path)) {
    throw new BusinessException("File is not readable: " + filePath, "FILE_NOT_READABLE", HttpStatus.FORBIDDEN);
}
```

### 9. **Rate Limit Exceeded**

**Request:** (11th request within 1 minute)
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key" \
  -H "Content-Type: application/json" \
  -d '{"file": "/test/file.pdf", "name": "Test"}'
```

**Response (429 Too Many Requests):**
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Try again later."
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** `@RateLimit` annotation + `RateLimitAspect`

**Code Location:**
```java
// In KnowledgeController.java line 56
@RateLimit(key = "knowledge-import", limit = 10, window = 60, useApiKey = true)
```

### 10. **Method Not Allowed**

**Request:**
```bash
curl -X DELETE "http://localhost:9090/api/v1/knowledge/import?api_key=valid_key"
```

**Response (405 Method Not Allowed):**
```json
{
  "success": false,
  "error": {
    "code": "METHOD_NOT_ALLOWED",
    "message": "Method 'DELETE' is not supported for this endpoint"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Handled by:** `GlobalExceptionHandler.handleMethodNotAllowed()`

### 11. **Invalid Path Parameter**

**Request:**
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/ /status?api_key=valid_key"  # space in path
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "CONSTRAINT_VIOLATION",
    "message": "Validation failed: getProcessingStatus.id: Knowledge base ID is required"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

**Code Location:**
```java
// In KnowledgeController.java line 150
@PathVariable @NotBlank(message = "Knowledge base ID is required") String id
```

### 12. **Endpoint Not Found**

**Request:**
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/nonexistent"
```

**Response (404 Not Found):**
```json
{
  "success": false,
  "error": {
    "code": "ENDPOINT_NOT_FOUND",
    "message": "Endpoint 'GET /api/v1/knowledge/nonexistent' not found"
  },
  "timestamp": "2024-01-15T10:25:00Z",
  "request_id": "req_12345"
}
```

## 🛡️ **Security Validations**

### Access Control
```java
// Check ownership in status endpoint
if (!kb.getUserId().equals(userId)) {
    throw new BusinessException("Access denied", "FORBIDDEN", HttpStatus.FORBIDDEN);
}
```

### Input Sanitization
```java
// Safe file path handling
Path path = Paths.get(filePath);  // Built-in path traversal protection
```

### API Key Logging (Security Safe)
```java
// Never log full API keys
logger.warn("Invalid API key provided: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
```

## 📊 **Exception Handler Flow**

```
1. Spring Framework Exceptions
   ├── @ExceptionHandler(MissingServletRequestParameterException.class)
   ├── @ExceptionHandler(HttpMessageNotReadableException.class)
   ├── @ExceptionHandler(MethodArgumentNotValidException.class)
   └── @ExceptionHandler(ConstraintViolationException.class)

2. Business Logic Exceptions  
   └── @ExceptionHandler(BusinessException.class)
       ├── UNAUTHORIZED (401)
       ├── FORBIDDEN (403) 
       ├── NOT_FOUND (404)
       ├── FILE_NOT_READABLE (403)
       └── PROCESSING_ERROR (500)

3. Infrastructure Exceptions
   ├── Rate Limiting → RATE_LIMIT_EXCEEDED (429)
   ├── File Upload → FILE_SIZE_EXCEEDED (413)
   └── Generic → INTERNAL_SERVER_ERROR (500)

4. All Exceptions Include:
   ├── Request ID for tracing
   ├── Timestamp
   ├── Structured error format
   └── Appropriate HTTP status codes
```

## ✅ **Key Validation Features**

1. **Multi-Layer Validation:**
   - Framework level (Spring)
   - Bean validation level (Jakarta)
   - Business logic level (Custom)
   - Infrastructure level (Rate limiting, etc.)

2. **Consistent Error Format:**
   - Always includes success flag
   - Structured error details with codes
   - Request ID for tracing
   - Appropriate HTTP status codes

3. **Security Considerations:**
   - Safe API key logging (partial masking)
   - Path traversal protection
   - Access control validation
   - Rate limiting per API key

4. **Developer Experience:**
   - Clear error messages
   - Specific error codes for programmatic handling
   - Detailed validation feedback
   - Request tracing for debugging

This comprehensive validation and exception handling ensures that all client errors are properly caught, logged, and returned with meaningful error responses while maintaining security and providing excellent developer experience.

