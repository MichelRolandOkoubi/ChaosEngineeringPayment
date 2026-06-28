package com.chaos.payment.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PaymentCommandResult(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("success") boolean success,
        @JsonProperty("externalReference") String externalReference,
        @JsonProperty("status") String status,
        @JsonProperty("paymentUrl") String paymentUrl,
        @JsonProperty("errorCode") String errorCode,
        @JsonProperty("errorMessage") String errorMessage
) {
    public static PaymentCommandResult success(String transactionId, String externalRef, String status, String url) {
        return new PaymentCommandResult(transactionId, true, externalRef, status, url, null, null);
    }

    public static PaymentCommandResult failure(String transactionId, String errorCode, String message) {
        return new PaymentCommandResult(transactionId, false, null, "FAILED", null, errorCode, message);
    }
}
