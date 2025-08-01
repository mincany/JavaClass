package com.example.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
public class PineconeService {

    private final WebClient pineconeWebClient;
    private final String indexName;
    private final ObjectMapper objectMapper;

    @Autowired
    public PineconeService(WebClient pineconeWebClient, String pineconeIndexName) {
        this.pineconeWebClient = pineconeWebClient;
        this.indexName = pineconeIndexName;
        this.objectMapper = new ObjectMapper();
    }

    public void upsertVector(String knowledgeBaseId, List<Double> embedding, String text) {
        try {
            // Create vector ID
            String vectorId = "chunk_" + UUID.randomUUID().toString().substring(0, 8);

            // Prepare metadata
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("knowledge_base_id", knowledgeBaseId);
            metadata.put("text_content", text);
            metadata.put("chunk_index", 0);

            // Prepare vector object
            ObjectNode vector = objectMapper.createObjectNode();
            vector.put("id", vectorId);
            vector.set("values", objectMapper.valueToTree(embedding));
            vector.set("metadata", metadata);

            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode vectors = objectMapper.createArrayNode();
            vectors.add(vector);
            requestBody.set("vectors", vectors);
            requestBody.put("namespace", knowledgeBaseId);

            // Make API call
            Mono<JsonNode> response = pineconeWebClient.post()
                    .uri("/vectors/upsert")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            JsonNode result = response.block();
            
            if (result == null || !result.has("upsertedCount")) {
                throw new RuntimeException("Invalid response from Pinecone upsert API");
            }

            int upsertedCount = result.get("upsertedCount").asInt();
            if (upsertedCount == 0) {
                throw new RuntimeException("No vectors were upserted to Pinecone");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert vector to Pinecone: " + e.getMessage(), e);
        }
    }

    public String queryVector(String knowledgeBaseId, List<Double> queryEmbedding) {
        try {
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("namespace", knowledgeBaseId);
            requestBody.put("topK", 3);
            requestBody.put("includeMetadata", true);
            requestBody.set("vector", objectMapper.valueToTree(queryEmbedding));

            // Make API call
            Mono<JsonNode> response = pineconeWebClient.post()
                    .uri("/query")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            JsonNode result = response.block();
            
            if (result == null || !result.has("matches") || !result.get("matches").isArray()) {
                throw new RuntimeException("Invalid response from Pinecone query API");
            }

            ArrayNode matches = (ArrayNode) result.get("matches");
            if (matches.size() == 0) {
                return "No relevant content found in the knowledge base.";
            }

            // Extract and combine text from matches
            StringBuilder contextBuilder = new StringBuilder();
            for (JsonNode match : matches) {
                if (match.has("metadata") && match.get("metadata").has("text_content")) {
                    String textContent = match.get("metadata").get("text_content").asText();
                    if (!textContent.isEmpty()) {
                        contextBuilder.append(textContent).append("\n\n");
                    }
                }
            }

            String context = contextBuilder.toString().trim();
            return context.isEmpty() ? "No relevant content found in the knowledge base." : context;

        } catch (Exception e) {
            throw new RuntimeException("Failed to query vector from Pinecone: " + e.getMessage(), e);
        }
    }

    public void deleteNamespace(String knowledgeBaseId) {
        try {
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("deleteAll", true);
            requestBody.put("namespace", knowledgeBaseId);

            // Make API call
            Mono<JsonNode> response = pineconeWebClient.post()
                    .uri("/vectors/delete")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            // For POC, we don't need to check the response
            response.block();

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete namespace from Pinecone: " + e.getMessage(), e);
        }
    }
} 