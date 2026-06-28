package com.chaos.payment.application.command;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@RegisterForReflection
public record InitiatePaymentCommand(
        @NotBlank(message = "User ID is required") String userId,

        @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be greater than 0") @DecimalMax(value = "10000000.00", message = "Amount exceeds maximum limit") BigDecimal amount,

        @NotBlank(message = "Currency code is required") @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid currency code format") String currencyCode,

        @NotBlank(message = "Provider ID is required") String providerId,

        String description,

        @NotBlank(message = "Phone number is required for mobile money") String phoneNumber,

        String callbackUrl,

        Map<String, String> metadata) {
}
