package com.chaos.payment.domain.service;
import com.chaos.payment.domain.model.Payment;
public interface ProviderRoutingService {
    ProviderResponse route(Payment payment, Object command);
}