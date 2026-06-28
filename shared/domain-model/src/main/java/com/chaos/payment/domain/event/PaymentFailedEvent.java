package com.chaos.payment.domain.event;
import com.chaos.payment.domain.valueobject.PaymentErrorCode;
import lombok.Value;
import java.time.Instant;
@Value
public class PaymentFailedEvent implements DomainEvent {
    String transactionId;
    String reason;
    PaymentErrorCode errorCode;
    int retryCount;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 3; }
    @Override public String getCorrelationId() { return null; }
}