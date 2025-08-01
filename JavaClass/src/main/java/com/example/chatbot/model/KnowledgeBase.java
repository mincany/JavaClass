package com.example.chatbot.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbBean
public class KnowledgeBase {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String fileName;
    private Long fileSize;
    private String status;
    private String pineconeNamespace;
    private Instant createdAt;
    private Instant updatedAt;

    public KnowledgeBase() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public KnowledgeBase(String id, String userId, String fileName, String status) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.status = status;
        this.pineconeNamespace = id;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPineconeNamespace() {
        return pineconeNamespace;
    }

    public void setPineconeNamespace(String pineconeNamespace) {
        this.pineconeNamespace = pineconeNamespace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
} 