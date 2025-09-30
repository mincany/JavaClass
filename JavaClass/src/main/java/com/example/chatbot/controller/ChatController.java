package com.example.chatbot.controller;

import com.example.chatbot.annotation.RateLimit;
import com.example.chatbot.dto.ApiResponse;
import com.example.chatbot.dto.ChatRequest;
import com.example.chatbot.dto.ChatResponse;
import com.example.chatbot.exception.BusinessException;
import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import com.example.chatbot.service.OpenAiService;
import com.example.chatbot.service.PineconeService;
import com.example.chatbot.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@Validated
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final OpenAiService openAiService;
    private final PineconeService pineconeService;
    private final UserService userService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    public ChatController(OpenAiService openAiService, PineconeService pineconeService,
                         UserService userService, KnowledgeBaseRepository knowledgeBaseRepository) {
        this.openAiService = openAiService;
        this.pineconeService = pineconeService;
        this.userService = userService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * Enhanced chat query endpoint with keyword identification and top-k context retrieval
     * 
     * This endpoint:
     * - Identifies keywords in the user question for better context matching
     * - Uses user namespace isolation in Pinecone
     * - Retrieves top-k most relevant chunks with similarity scoring
     * - Provides detailed source information in response
     * 
     * @param request Chat request with question and knowledge base ID
     * @param apiKey User's API key for authentication
     * @param topK Number of top chunks to retrieve (default: 5, max: 20)
     * @param scoreThreshold Minimum similarity score threshold (default: 0.7)
     * @return Enhanced chat response with context sources
     */
    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @RateLimit(key = "chat-query", limit = 50, window = 60, useApiKey = true)
    public ResponseEntity<ApiResponse<ChatResponse>> query(
            @Valid @RequestBody ChatRequest request,
            @RequestParam("api_key") @NotBlank(message = "API key is required") String apiKey,
            @RequestParam(value = "top_k", defaultValue = "5") int topK,
            @RequestParam(value = "score_threshold", defaultValue = "0.7") double scoreThreshold) {
        
        logger.info("Processing chat query: knowledge_base={}, question_length={}, top_k={}", 
                request.getKnowledgeBaseId(), request.getQuestion().length(), topK);
        
        try {
            // Validate parameters
            if (topK < 1 || topK > 20) {
                throw new BusinessException("top_k must be between 1 and 20", "INVALID_PARAMETER", HttpStatus.BAD_REQUEST);
            }
            
            if (scoreThreshold < 0.0 || scoreThreshold > 1.0) {
                throw new BusinessException("score_threshold must be between 0.0 and 1.0", "INVALID_PARAMETER", HttpStatus.BAD_REQUEST);
            }

            // Validate API key and get user ID
            String userId = userService.getUserIdFromApiKey(apiKey);
            if (userId == null) {
                logger.warn("Invalid API key provided for chat query");
                throw new BusinessException("Invalid API key", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
            }

            // Find and validate knowledge base
            var kbOptional = knowledgeBaseRepository.findById(request.getKnowledgeBaseId());
            if (kbOptional.isEmpty()) {
                logger.warn("Knowledge base not found: {}", request.getKnowledgeBaseId());
                throw new BusinessException("Knowledge base not found", "NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            KnowledgeBase kb = kbOptional.get();
            
            // Check ownership
            if (!kb.getUserId().equals(userId)) {
                logger.warn("Access denied for knowledge base: {} by user: {}", request.getKnowledgeBaseId(), userId);
                throw new BusinessException("Access denied", "FORBIDDEN", HttpStatus.FORBIDDEN);
            }
            
            // Check if knowledge base is ready
            if (!"completed".equals(kb.getStatus())) {
                throw new BusinessException("Knowledge base is not ready. Current status: " + kb.getStatus(), 
                        "KNOWLEDGE_BASE_NOT_READY", HttpStatus.PRECONDITION_FAILED);
            }

            // Extract keywords from question for better context matching
            String enhancedQuery = extractKeywords(request.getQuestion());
            logger.debug("Enhanced query with keywords: {}", enhancedQuery);

            // Convert enhanced question to embedding
            List<Double> questionEmbedding = openAiService.createEmbedding(enhancedQuery);

            // Search Pinecone for relevant context chunks in user's namespace
            List<PineconeService.ContextChunk> contextChunks = pineconeService.queryVectors(
                    userId, request.getKnowledgeBaseId(), questionEmbedding, topK, scoreThreshold);

            if (contextChunks.isEmpty()) {
                logger.info("No relevant context found for question in knowledge base: {}", request.getKnowledgeBaseId());
                
                ChatResponse chatResponse = new ChatResponse(
                        "I couldn't find relevant information in your knowledge base to answer this question. " +
                        "Please try rephrasing your question or check if the content has been properly uploaded.",
                        request.getKnowledgeBaseId()
                );
                
                if (request.getSessionId() != null) {
                    chatResponse.setSessionId(request.getSessionId());
                }
                
                return ResponseEntity.ok(ApiResponse.success(chatResponse));
            }

            // Combine context chunks
            String combinedContext = contextChunks.stream()
                    .map(PineconeService.ContextChunk::getContent)
                    .collect(Collectors.joining("\n\n"));

            logger.debug("Retrieved {} context chunks, total context length: {}", 
                    contextChunks.size(), combinedContext.length());

            // Generate response using OpenAI with enhanced context
            String aiResponse = openAiService.generateChatResponse(combinedContext, request.getQuestion());

            // Create enhanced response object with source information
            ChatResponse chatResponse = new ChatResponse(aiResponse, request.getKnowledgeBaseId());
            if (request.getSessionId() != null) {
                chatResponse.setSessionId(request.getSessionId());
            }
            
            // Add source information from context chunks
            List<ChatResponse.SourceInfo> sources = contextChunks.stream()
                    .map(chunk -> new ChatResponse.SourceInfo(
                            chunk.getVectorId(),
                            kb.getName() != null ? kb.getName() : kb.getFileName(),
                            chunk.getChunkIndex(),
                            chunk.getScore()
                    ))
                    .collect(Collectors.toList());
            
            chatResponse.setSources(sources);
            chatResponse.setContextChunksUsed(contextChunks.size());
            chatResponse.setMinScore(contextChunks.stream().mapToDouble(PineconeService.ContextChunk::getScore).min().orElse(0.0));
            chatResponse.setMaxScore(contextChunks.stream().mapToDouble(PineconeService.ContextChunk::getScore).max().orElse(0.0));

            logger.info("Successfully processed chat query for knowledge base: {} with {} context chunks", 
                    request.getKnowledgeBaseId(), contextChunks.size());

            return ResponseEntity.ok(ApiResponse.success(chatResponse));

        } catch (BusinessException e) {
            // BusinessExceptions are already handled by GlobalExceptionHandler
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error processing chat query: {}", e.getMessage(), e);
            throw new BusinessException("Failed to process query: " + e.getMessage(), 
                    "PROCESSING_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Extract and enhance keywords from user question for better semantic search
     */
    private String extractKeywords(String question) {
        if (question == null || question.trim().isEmpty()) {
            return question;
        }
        
        // Simple keyword extraction and enhancement
        // In production, you might want to use NLP libraries like Stanford CoreNLP or spaCy
        String cleanQuestion = question.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ") // Remove special characters
                .replaceAll("\\s+", " ") // Normalize whitespace
                .trim();
        
        // Remove common stop words that don't add semantic value
        Set<String> stopWords = Set.of(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", 
                "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", 
                "did", "will", "would", "could", "should", "may", "might", "can", "what", "how", 
                "when", "where", "why", "who", "which", "this", "that", "these", "those"
        );
        
        String[] words = cleanQuestion.split("\\s+");
        List<String> keywords = Arrays.stream(words)
                .filter(word -> word.length() > 2) // Remove very short words
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toList());
        
        // If we filtered out too many words, use the original question
        if (keywords.isEmpty() || keywords.size() < 2) {
            return question;
        }
        
        // Combine keywords with original question for comprehensive search
        String keywordString = String.join(" ", keywords);
        return question + " " + keywordString;
    }
} 