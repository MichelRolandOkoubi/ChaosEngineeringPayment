package com.chaos.payment.infrastructure.provider.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@RegisterForReflection
public class ProviderResponse {
    private final boolean success;
    private final String externalReference;
    private final String paymentUrl;
    private final String status;
    private final String errorCode;
    private final String errorMessage;

    public static ProviderResponse failure(String errorCode, String message) {
        return ProviderResponse.builder()
                .success(false)
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }
}
