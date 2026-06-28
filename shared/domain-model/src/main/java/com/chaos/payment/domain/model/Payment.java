package com.chaos.payment.domain.model;

import com.chaos.payment.domain.event.*;
import com.chaos.payment.domain.valueobject.*;
import com.chaos.payment.domain.exception.MaxRetriesExceededException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment Aggregate Root - Domain Driven Design
 * Implements Event Sourcing pattern
 */
@RegisterForReflection
public class Payment {

    // Identity
    private TransactionId transactionId;
    private UserId userId;

    // Value Objects
    private Money amount;
    private PaymentProvider provider;
    private PaymentStatus status;
    private PaymentMethod method;
    private Currency currency;

    // Metadata
    private String description;
    private String externalReference;
    private Map<String, String> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private int retryCount;
    private int version;

    // Chaos Engineering Fields
    private boolean chaosInjected;
    private String chaosScenario;

    // Event Sourcing - Domain Events
    @JsonIgnore
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    // ============================================
    // Factory Method
    // ============================================
    public static Payment create(
            String userId,
            BigDecimal amount,
            String currencyCode,
            String providerId,
            String description,
            Map<String, String> metadata) {

        Payment payment = new Payment();
        payment.transactionId = TransactionId.generate();
        payment.userId = new UserId(userId);
        payment.amount = new Money(amount, new Currency(currencyCode));
        payment.provider = PaymentProvider.fromCode(providerId);
        payment.status = PaymentStatus.INITIATED;
        payment.method = determinePaymentMethod(providerId);
        payment.currency = new Currency(currencyCode);
        payment.description = description;
        payment.metadata = metadata != null ? metadata : new HashMap<>();
        payment.createdAt = Instant.now();
        payment.updatedAt = Instant.now();
        payment.retryCount = 0;
        payment.version = 0;
        payment.chaosInjected = false;

        // Raise Domain Event
        payment.raiseEvent(new PaymentInitiatedEvent(
                payment.transactionId.getValue(),
                payment.userId.getValue(),
                payment.amount.getAmount(),
                payment.currency.getCode(),
                payment.provider.getCode(),
                payment.description,
                payment.createdAt));

        return payment;
    }

    // ============================================
    // Business Methods
    // ============================================
    public void processPayment() {
        validateCanProcess();
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();

        raiseEvent(new PaymentProcessingEvent(
                transactionId.getValue(),
                provider.getCode(),
                Instant.now()));
    }

    public void completePayment(String externalReference) {
        if (!PaymentStatus.PROCESSING.equals(this.status)) {
            throw new IllegalStateException(
                    "Payment cannot be completed from status: " + this.status);
        }

        this.externalReference = externalReference;
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();

        raiseEvent(new PaymentCompletedEvent(
                transactionId.getValue(),
                externalReference,
                amount.getAmount(),
                currency.getCode(),
                provider.getCode(),
                Instant.now()));
    }

    public void failPayment(String reason, PaymentErrorCode errorCode) {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();

        raiseEvent(new PaymentFailedEvent(
                transactionId.getValue(),
                reason,
                errorCode,
                retryCount,
                Instant.now()));
    }

    public void retryPayment() {
        if (retryCount >= 3) {
            throw new MaxRetriesExceededException(
                    "Maximum retry count exceeded for transaction: " + transactionId);
        }

        this.retryCount++;
        this.status = PaymentStatus.RETRYING;
        this.updatedAt = Instant.now();

        raiseEvent(new PaymentRetryInitiatedEvent(
                transactionId.getValue(),
                retryCount,
                provider.getCode(),
                Instant.now()));
    }

    public void refundPayment(BigDecimal refundAmount, String reason) {
        if (!PaymentStatus.COMPLETED.equals(this.status)) {
            throw new IllegalStateException(
                    "Only completed payments can be refunded");
        }

        if (refundAmount.compareTo(amount.getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount cannot exceed original payment amount");
        }

        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();

        raiseEvent(new PaymentRefundedEvent(
                transactionId.getValue(),
                refundAmount,
                currency.getCode(),
                reason,
                Instant.now()));
    }

    public void injectChaos(String scenario) {
        this.chaosInjected = true;
        this.chaosScenario = scenario;

        raiseEvent(new ChaosInjectedEvent(
                transactionId.getValue(),
                scenario,
                Instant.now()));
    }

    // ============================================
    // Reconstitution from Events (Event Sourcing)
    // ============================================
    public static Payment reconstitute(List<DomainEvent> events) {
        Payment payment = new Payment();
        events.forEach(payment::apply);
        return payment;
    }

    private void apply(DomainEvent event) {
        if (event instanceof PaymentInitiatedEvent e) {
            this.transactionId = new TransactionId(e.getTransactionId());
            this.userId = new UserId(e.getUserId());
            this.amount = new Money(e.getAmount(), new Currency(e.getCurrencyCode()));
            this.currency = new Currency(e.getCurrencyCode());
            this.provider = PaymentProvider.fromCode(e.getProviderId());
            this.status = PaymentStatus.INITIATED;
            this.createdAt = e.getOccurredAt();
        } else if (event instanceof PaymentProcessingEvent e) {
            this.status = PaymentStatus.PROCESSING;
            this.updatedAt = e.getOccurredAt();
        } else if (event instanceof PaymentCompletedEvent e) {
            this.status = PaymentStatus.COMPLETED;
            this.externalReference = e.getExternalReference();
            this.updatedAt = e.getOccurredAt();
        } else if (event instanceof PaymentFailedEvent e) {
            this.status = PaymentStatus.FAILED;
            this.updatedAt = e.getOccurredAt();
        } else if (event instanceof PaymentRetryInitiatedEvent e) {
            this.retryCount = e.getRetryCount();
            this.status = PaymentStatus.RETRYING;
            this.updatedAt = e.getOccurredAt();
        } else if (event instanceof PaymentRefundedEvent e) {
            this.status = PaymentStatus.REFUNDED;
            this.updatedAt = e.getOccurredAt();
        }

        this.version++;
    }

    // ============================================
    // Private Helpers
    // ============================================
    private void validateCanProcess() {
        if (!PaymentStatus.INITIATED.equals(this.status) &&
                !PaymentStatus.RETRYING.equals(this.status)) {
            throw new IllegalStateException(
                    "Payment cannot be processed from status: " + this.status);
        }
    }

    private static PaymentMethod determinePaymentMethod(String providerId) {
        return switch (providerId.toUpperCase()) {
            case "ORANGE", "MOOV", "MTN", "WAVE", "AIRTEL", "MPESA" ->
                PaymentMethod.MOBILE_MONEY;
            case "VISA", "MASTERCARD" ->
                PaymentMethod.CARD;
            case "BTC" ->
                PaymentMethod.CRYPTO;
            case "PI_SPI_BCEAO" ->
                PaymentMethod.INTERBANK;
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + providerId);
        };
    }

    private void raiseEvent(DomainEvent event) {
        uncommittedEvents.add(event);
        apply(event);
        this.version--; // Undo version increment from apply()
    }

    // ============================================
    // Getters
    // ============================================
    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public UserId getUserId() {
        return userId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getVersion() {
        return version;
    }

    public boolean isChaosInjected() {
        return chaosInjected;
    }

    public String getChaosScenario() {
        return chaosScenario;
    }
}