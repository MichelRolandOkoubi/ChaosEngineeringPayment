package com.chaos.payment.infrastructure.messaging;
import com.chaos.payment.application.command.InitiatePaymentCommand;
import lombok.Value;
@Value
public class PaymentCommandFallbackEvent {
    InitiatePaymentCommand command;
}