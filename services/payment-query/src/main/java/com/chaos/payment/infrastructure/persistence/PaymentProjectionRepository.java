package com.chaos.payment.infrastructure.persistence;

import com.chaos.payment.application.query.ListPaymentsQuery;
import com.chaos.payment.application.query.PaymentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class PaymentProjectionRepository {

    private static final Logger LOG = Logger.getLogger(PaymentProjectionRepository.class);

    @Inject
    DynamoDbClient dynamoDb;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "dynamodb.table.transactions", defaultValue = "PaymentTransactions")
    String transactionsTable;

    public Optional<PaymentView> findById(String transactionId) {
        try {
            GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(transactionsTable)
                    .key(Map.of("transactionId", AttributeValue.fromS(transactionId)))
                    .build());

            if (!response.hasItem() || response.item().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toView(response.item()));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch payment %s", transactionId);
            return Optional.empty();
        }
    }

    public List<PaymentView> findByQuery(ListPaymentsQuery query) {
        try {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(transactionsTable)
                    .limit(query.size());

            if (query.userId() != null) {
                builder.indexName("userId-index")
                        .keyConditionExpression("userId = :uid")
                        .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(query.userId())));
            } else if (query.providerId() != null) {
                builder.indexName("providerId-index")
                        .keyConditionExpression("providerId = :pid")
                        .expressionAttributeValues(Map.of(":pid", AttributeValue.fromS(query.providerId())));
            } else if (query.status() != null) {
                builder.indexName("status-index")
                        .keyConditionExpression("#s = :status")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(query.status())));
            } else {
                return List.of();
            }

            QueryResponse response = dynamoDb.query(builder.build());
            return response.items().stream().map(this::toView).toList();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to query payments: %s", query);
            return List.of();
        }
    }

    public void upsert(PaymentView view) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("transactionId", AttributeValue.fromS(view.transactionId()));
            item.put("userId", AttributeValue.fromS(view.userId()));
            item.put("amount", AttributeValue.fromN(view.amount().toPlainString()));
            item.put("currencyCode", AttributeValue.fromS(view.currencyCode()));
            item.put("providerId", AttributeValue.fromS(view.providerId()));
            item.put("status", AttributeValue.fromS(view.status()));
            item.put("paymentMethod", AttributeValue.fromS(view.paymentMethod()));
            item.put("retryCount", AttributeValue.fromN(String.valueOf(view.retryCount())));
            item.put("chaosInjected", AttributeValue.fromBool(view.chaosInjected()));
            item.put("createdAt", AttributeValue.fromS(view.createdAt().toString()));
            item.put("updatedAt", AttributeValue.fromS(Instant.now().toString()));

            if (view.description() != null) item.put("description", AttributeValue.fromS(view.description()));
            if (view.externalReference() != null) item.put("externalReference", AttributeValue.fromS(view.externalReference()));
            if (view.paymentUrl() != null) item.put("paymentUrl", AttributeValue.fromS(view.paymentUrl()));
            if (view.chaosScenario() != null) item.put("chaosScenario", AttributeValue.fromS(view.chaosScenario()));

            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(transactionsTable)
                    .item(item)
                    .build());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to upsert payment projection %s", view.transactionId());
        }
    }

    private PaymentView toView(Map<String, AttributeValue> item) {
        return new PaymentView(
                str(item, "transactionId"),
                str(item, "userId"),
                item.containsKey("amount") ? new BigDecimal(item.get("amount").n()) : BigDecimal.ZERO,
                str(item, "currencyCode"),
                str(item, "providerId"),
                str(item, "providerName"),
                str(item, "status"),
                str(item, "paymentMethod"),
                str(item, "description"),
                str(item, "externalReference"),
                str(item, "paymentUrl"),
                item.containsKey("retryCount") ? Integer.parseInt(item.get("retryCount").n()) : 0,
                item.containsKey("chaosInjected") && item.get("chaosInjected").bool(),
                str(item, "chaosScenario"),
                null,
                item.containsKey("createdAt") ? Instant.parse(item.get("createdAt").s()) : Instant.now(),
                item.containsKey("updatedAt") ? Instant.parse(item.get("updatedAt").s()) : Instant.now()
        );
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null && v.s() != null ? v.s() : null;
    }
}
