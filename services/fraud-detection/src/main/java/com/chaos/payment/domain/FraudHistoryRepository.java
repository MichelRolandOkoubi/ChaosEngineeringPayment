package com.chaos.payment.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class FraudHistoryRepository {

    private static final Logger LOG = Logger.getLogger(FraudHistoryRepository.class);

    @Inject
    DynamoDbClient dynamoDb;

    @ConfigProperty(name = "dynamodb.table.fraud-history", defaultValue = "FraudHistory")
    String fraudHistoryTable;

    @ConfigProperty(name = "dynamodb.table.blacklist", defaultValue = "FraudBlacklist")
    String blacklistTable;

    public int countRecentTransactions(String userId, int windowSeconds) {
        try {
            long since = Instant.now().minusSeconds(windowSeconds).toEpochMilli();
            QueryResponse response = dynamoDb.query(QueryRequest.builder()
                    .tableName(fraudHistoryTable)
                    .indexName("userId-timestamp-index")
                    .keyConditionExpression("userId = :uid AND #ts >= :since")
                    .expressionAttributeNames(Map.of("#ts", "timestamp"))
                    .expressionAttributeValues(Map.of(
                            ":uid", AttributeValue.fromS(userId),
                            ":since", AttributeValue.fromN(String.valueOf(since))))
                    .select(Select.COUNT)
                    .build());
            return response.count();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to count recent transactions for user %s", userId);
            return 0;
        }
    }

    public boolean isDuplicateTransaction(String userId, BigDecimal amount,
                                          String providerId, int windowSeconds) {
        try {
            long since = Instant.now().minusSeconds(windowSeconds).toEpochMilli();
            QueryResponse response = dynamoDb.query(QueryRequest.builder()
                    .tableName(fraudHistoryTable)
                    .indexName("userId-timestamp-index")
                    .keyConditionExpression("userId = :uid AND #ts >= :since")
                    .filterExpression("amount = :amount AND providerId = :pid")
                    .expressionAttributeNames(Map.of("#ts", "timestamp"))
                    .expressionAttributeValues(Map.of(
                            ":uid", AttributeValue.fromS(userId),
                            ":since", AttributeValue.fromN(String.valueOf(since)),
                            ":amount", AttributeValue.fromN(amount.toPlainString()),
                            ":pid", AttributeValue.fromS(providerId)))
                    .select(Select.COUNT)
                    .build());
            return response.count() > 0;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to check duplicate for user %s", userId);
            return false;
        }
    }

    public boolean isBlacklisted(String userId) {
        try {
            GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(blacklistTable)
                    .key(Map.of("userId", AttributeValue.fromS(userId)))
                    .build());
            return response.hasItem() && !response.item().isEmpty();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to check blacklist for user %s", userId);
            return false;
        }
    }

    public int countRecentRetries(String userId, int windowSeconds) {
        try {
            long since = Instant.now().minusSeconds(windowSeconds).toEpochMilli();
            QueryResponse response = dynamoDb.query(QueryRequest.builder()
                    .tableName(fraudHistoryTable)
                    .indexName("userId-timestamp-index")
                    .keyConditionExpression("userId = :uid AND #ts >= :since")
                    .filterExpression("eventType = :retry")
                    .expressionAttributeNames(Map.of("#ts", "timestamp"))
                    .expressionAttributeValues(Map.of(
                            ":uid", AttributeValue.fromS(userId),
                            ":since", AttributeValue.fromN(String.valueOf(since)),
                            ":retry", AttributeValue.fromS("RETRY")))
                    .select(Select.COUNT)
                    .build());
            return response.count();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to count retries for user %s", userId);
            return 0;
        }
    }

    public void recordTransaction(String userId, String transactionId,
                                   BigDecimal amount, String providerId) {
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(fraudHistoryTable)
                    .item(Map.of(
                            "transactionId", AttributeValue.fromS(transactionId),
                            "userId", AttributeValue.fromS(userId),
                            "amount", AttributeValue.fromN(amount.toPlainString()),
                            "providerId", AttributeValue.fromS(providerId),
                            "timestamp", AttributeValue.fromN(String.valueOf(Instant.now().toEpochMilli())),
                            "eventType", AttributeValue.fromS("TRANSACTION"),
                            "ttl", AttributeValue.fromN(String.valueOf(Instant.now().plusSeconds(86400).getEpochSecond()))
                    ))
                    .build());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to record transaction history for %s", transactionId);
        }
    }
}
