package com.chaos.payment.domain.event;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
@Value
public class PaymentRefundedEvent implements DomainEvent {
    String transactionId;
    BigDecimal refundAmount;
    String currencyCode;
    String reason;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 4; }
    @Override public String getCorrelationId() { return null; }
}