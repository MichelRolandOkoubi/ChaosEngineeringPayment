package com.chaos.payment.domain.service;
import lombok.Value;
@Value
public class FraudAnalysis {
    boolean fraudulent;
    String reason;
}