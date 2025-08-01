package com.example.chatbot.controller;

import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import com.example.chatbot.service.FileService;
import com.example.chatbot.service.OpenAiService;
import com.example.chatbot.service.PineconeService;
import com.example.chatbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final FileService fileService;
    private final OpenAiService openAiService;
    private final PineconeService pineconeService;
    private final UserService userService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    public KnowledgeController(FileService fileService, OpenAiService openAiService, 
                              PineconeService pineconeService, UserService userService,
                              KnowledgeBaseRepository knowledgeBaseRepository) {
        this.fileService = fileService;
        this.openAiService = openAiService;
        this.pineconeService = pineconeService;
        this.userService = userService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("api_key") String apiKey,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            // Validate API key and get user ID
            String userId = userService.getUserIdFromApiKey(apiKey);
            if (userId == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(401).body(errorResponse);
            }

            // Extract text from file
            String text = fileService.extractText(file);

            // Generate embedding
            List<Double> embedding = openAiService.createEmbedding(text);

            // Store in Pinecone
            String knowledgeBaseId = "kb_" + UUID.randomUUID().toString().substring(0, 8);
            pineconeService.upsertVector(knowledgeBaseId, embedding, text);

            // Save metadata to DynamoDB
            KnowledgeBase kb = new KnowledgeBase(knowledgeBaseId, userId, file.getOriginalFilename(), "processed");
            if (name != null) kb.setName(name);
            if (description != null) kb.setDescription(description);
            kb.setFileSize(file.getSize());
            
            knowledgeBaseRepository.save(kb);

            Map<String, String> response = new HashMap<>();
            response.put("knowledge_base_id", knowledgeBaseId);
            response.put("status", "processed");
            response.put("message", "File processed successfully");
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to process file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getKnowledgeBase(
            @PathVariable String id,
            @RequestParam("api_key") String apiKey) {
        
        try {
            // Validate API key and get user ID
            String userId = userService.getUserIdFromApiKey(apiKey);
            if (userId == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(401).body(errorResponse);
            }

            // Find knowledge base
            var kbOptional = knowledgeBaseRepository.findById(id);
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

            return ResponseEntity.ok(kb);

        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping
    public ResponseEntity<?> listKnowledgeBases(@RequestParam("api_key") String apiKey) {
        try {
            // Validate API key and get user ID
            String userId = userService.getUserIdFromApiKey(apiKey);
            if (userId == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(401).body(errorResponse);
            }

            List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findByUserId(userId);
            return ResponseEntity.ok(knowledgeBases);

        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 