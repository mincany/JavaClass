package com.example.chatbot.dto;

import java.util.List;

public class ChatResponse {
    private String response;
    private List<SourceInfo> sources;
    private String sessionId;
    private Integer tokensUsed;
    private String knowledgeBaseId;

    public ChatResponse() {}

    public ChatResponse(String response, String knowledgeBaseId) {
        this.response = response;
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<SourceInfo> getSources() {
        return sources;
    }

    public void setSources(List<SourceInfo> sources) {
        this.sources = sources;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public static class SourceInfo {
        private String chunkId;
        private Double relevanceScore;
        private String textPreview;

        public SourceInfo() {}

        public SourceInfo(String chunkId, Double relevanceScore, String textPreview) {
            this.chunkId = chunkId;
            this.relevanceScore = relevanceScore;
            this.textPreview = textPreview;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public Double getRelevanceScore() {
            return relevanceScore;
        }

        public void setRelevanceScore(Double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }

        public String getTextPreview() {
            return textPreview;
        }

        public void setTextPreview(String textPreview) {
            this.textPreview = textPreview;
        }
    }
} 