package com.chaos.payment.infrastructure.persistence;

import com.chaos.payment.domain.event.DomainEvent;
import com.chaos.payment.domain.repository.PaymentEventStore;
import com.chaos.payment.infrastructure.chaos.ChaosMonkey;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class DynamoDBEventStore implements PaymentEventStore {

    private static final Logger LOG = Logger.getLogger(DynamoDBEventStore.class);

    @Inject
    DynamoDbClient dynamoDbClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChaosMonkey chaosMonkey;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "dynamodb.event-store.table", defaultValue = "PaymentEventStore")
    String tableName;

    @ConfigProperty(name = "dynamodb.payments.table", defaultValue = "PaymentTransactions")
    String paymentsTable;

    @Override
    public void appendEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {

        if (events.isEmpty())
            return;

        // Chaos Engineering - Inject DynamoDB latency or failure
        chaosMonkey.maybeInjectDynamoDBChaos();

        List<TransactWriteItem> writeItems = new ArrayList<>();

        for (DomainEvent event : events) {
            Map<String, AttributeValue> item = buildEventItem(aggregateId, event, expectedVersion);

            writeItems.add(TransactWriteItem.builder()
                    .put(Put.builder()
                            .tableName(tableName)
                            .item(item)
                            .conditionExpression("attribute_not_exists(aggregateId) OR " +
                                    "sequenceNumber < :expectedVersion")
                            .expressionAttributeValues(Map.of(
                                    ":expectedVersion", AttributeValue.fromN(
                                            String.valueOf(expectedVersion + events.indexOf(event)))))
                            .build())
                    .build());
        }

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(writeItems)
                    .build());

            LOG.infof("Successfully appended %d events for aggregate: %s",
                    events.size(), aggregateId);

            meterRegistry.counter("eventstore.events.appended",
                    "count", String.valueOf(events.size())).increment(events.size());

        } catch (TransactionCanceledException e) {
            LOG.errorf(e, "Optimistic concurrency conflict for aggregate: %s", aggregateId);
            throw new ConcurrencyException("Concurrent modification detected", e);
        }
    }

    @Override
    public List<DomainEvent> getEvents(String aggregateId) {
        return getEventsAfterVersion(aggregateId, 0);
    }

    @Override
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {

        // Chaos Engineering - Simulate read failures
        chaosMonkey.maybeInjectDynamoDBChaos();

        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(
                        "aggregateId = :aggregateId AND sequenceNumber > :version")
                .expressionAttributeValues(Map.of(
                        ":aggregateId", AttributeValue.fromS(aggregateId),
                        ":version", AttributeValue.fromN(String.valueOf(version))))
                .consistentRead(true)
                .build();

        try {
            QueryResponse response = dynamoDbClient.query(request);

            return response.items().stream()
                    .map(this::deserializeEvent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve events for aggregate: %s", aggregateId);

            meterRegistry.counter("eventstore.read.error").increment();
            throw new EventStoreException("Failed to load events", e);
        }
    }

    @Override
    public void saveSnapshot(String aggregateId, Object snapshot, int version) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("aggregateId", AttributeValue.fromS(aggregateId + "#snapshot"));
        item.put("sequenceNumber", AttributeValue.fromN(String.valueOf(version)));
        item.put("snapshotData", AttributeValue.fromS(serializeToJson(snapshot)));
        item.put("timestamp", AttributeValue.fromS(Instant.now().toString()));
        item.put("version", AttributeValue.fromN(String.valueOf(version)));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        LOG.infof("Snapshot saved for aggregate: %s at version: %d", aggregateId, version);
    }

    // ============================================
    // Payment Read Model (CQRS Read Side)
    // ============================================
    public void updatePaymentProjection(String transactionId, Map<String, AttributeValue> attributes) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(paymentsTable)
                .key(Map.of(
                        "transactionId", AttributeValue.fromS(transactionId),
                        "timestamp", AttributeValue.fromS(Instant.now().toString())))
                .updateExpression(buildUpdateExpression(attributes))
                .expressionAttributeValues(attributes)
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        dynamoDbClient.updateItem(request);
    }

    // ============================================
    // Helpers
    // ============================================
    private Map<String, AttributeValue> buildEventItem(
            String aggregateId, DomainEvent event, int baseVersion) {

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("aggregateId", AttributeValue.fromS(aggregateId));
        item.put("sequenceNumber", AttributeValue.fromN(
                String.valueOf(baseVersion + 1)));
        item.put("eventType", AttributeValue.fromS(event.getClass().getSimpleName()));
        item.put("eventData", AttributeValue.fromS(serializeToJson(event)));
        item.put("occurredAt", AttributeValue.fromS(event.getOccurredAt().toString()));
        item.put("version", AttributeValue.fromN(String.valueOf(event.getVersion())));
        item.put("correlationId", AttributeValue.fromS(
                event.getCorrelationId() != null ? event.getCorrelationId() : UUID.randomUUID().toString()));

        return item;
    }

    private DomainEvent deserializeEvent(Map<String, AttributeValue> item) {
        try {
            String eventType = item.get("eventType").s();
            String eventData = item.get("eventData").s();

            Class<?> eventClass = Class.forName(
                    "com.chaos.payment.domain.event." + eventType);

            return (DomainEvent) objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize event");
            return null;
        }
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private String buildUpdateExpression(Map<String, AttributeValue> attributes) {
        StringBuilder sb = new StringBuilder("SET ");
        attributes.keySet().forEach(key -> sb.append(key).append(" = :").append(key).append(", "));
        return sb.toString().replaceAll(", $", "");
    }
}