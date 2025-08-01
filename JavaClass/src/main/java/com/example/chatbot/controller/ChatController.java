package com.example.chatbot.controller;

import com.example.chatbot.dto.ChatRequest;
import com.example.chatbot.dto.ChatResponse;
import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import com.example.chatbot.service.OpenAiService;
import com.example.chatbot.service.PineconeService;
import com.example.chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

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

    @PostMapping("/query")
    public ResponseEntity<?> query(
            @Valid @RequestBody ChatRequest request,
            @RequestParam("api_key") String apiKey) {
        
        try {
            // Validate API key and get user ID
            String userId = userService.getUserIdFromApiKey(apiKey);
            if (userId == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(401).body(errorResponse);
            }

            // Find and validate knowledge base
            var kbOptional = knowledgeBaseRepository.findById(request.getKnowledgeBaseId());
            if (kbOptional.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Knowledge base not found");
                return ResponseEntity.notFound().build();
            }

            KnowledgeBase kb = kbOptional.get();
            
            // Check ownership
            if (!kb.getUserId().equals(userId)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Access denied");
                return ResponseEntity.status(403).body(errorResponse);
            }

            // Convert question to embedding
            List<Double> questionEmbedding = openAiService.createEmbedding(request.getQuestion());

            // Search Pinecone for relevant context
            String context = pineconeService.queryVector(request.getKnowledgeBaseId(), questionEmbedding);

            // Generate response using OpenAI
            String response = openAiService.generateChatResponse(context, request.getQuestion());

            // Create response object
            ChatResponse chatResponse = new ChatResponse(response, request.getKnowledgeBaseId());
            if (request.getSessionId() != null) {
                chatResponse.setSessionId(request.getSessionId());
            }

            return ResponseEntity.ok(chatResponse);

        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to process query: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 