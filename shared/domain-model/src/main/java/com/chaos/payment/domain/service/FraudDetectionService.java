package com.chaos.payment.domain.service;
public interface FraudDetectionService {
    FraudAnalysis analyze(Object command);
}