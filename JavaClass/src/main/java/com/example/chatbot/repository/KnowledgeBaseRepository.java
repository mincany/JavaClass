package com.example.chatbot.repository;

import com.example.chatbot.model.KnowledgeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class KnowledgeBaseRepository {

    private final DynamoDbTable<KnowledgeBase> knowledgeBaseTable;

    @Autowired
    public KnowledgeBaseRepository(DynamoDbTable<KnowledgeBase> knowledgeBaseTable) {
        this.knowledgeBaseTable = knowledgeBaseTable;
    }

    public void save(KnowledgeBase knowledgeBase) {
        knowledgeBaseTable.putItem(knowledgeBase);
    }

    public Optional<KnowledgeBase> findById(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseTable.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(knowledgeBase);
    }

    public List<KnowledgeBase> findByUserId(String userId) {
        // For POC, we'll do a simple scan. In production, use GSI
        return knowledgeBaseTable.scan(ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("userId = :userId")
                        .putExpressionValue(":userId", AttributeValue.builder().s(userId).build())
                        .build())
                .build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        knowledgeBaseTable.deleteItem(Key.builder().partitionValue(id).build());
    }
} 