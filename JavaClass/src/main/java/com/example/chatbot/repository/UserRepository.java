package com.example.chatbot.repository;

import com.example.chatbot.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Optional;

@Repository
public class UserRepository {

    private final DynamoDbTable<User> userTable;

    @Autowired
    public UserRepository(DynamoDbTable<User> userTable) {
        this.userTable = userTable;
    }

    public void save(User user) {
        userTable.putItem(user);
    }

    public Optional<User> findById(String id) {
        User user = userTable.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(user);
    }

    public Optional<User> findByApiKey(String apiKey) {
        // For POC, we'll do a simple scan. In production, use GSI
        return userTable.scan(ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("apiKey = :apiKey")
                        .putExpressionValue(":apiKey", AttributeValue.builder().s(apiKey).build())
                        .build())
                .build())
                .items()
                .stream()
                .findFirst();
    }

    public Optional<User> findByEmail(String email) {
        // For POC, we'll do a simple scan. In production, use GSI
        return userTable.scan(ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("email = :email")
                        .putExpressionValue(":email", AttributeValue.builder().s(email).build())
                        .build())
                .build())
                .items()
                .stream()
                .findFirst();
    }
} 