package com.chaos.payment.domain.service;
import com.chaos.payment.domain.valueobject.PaymentErrorCode;
import lombok.Value;
@Value
public class ProviderResponse {
    boolean success;
    String externalReference;
    String errorMessage;
    PaymentErrorCode errorCode;
}