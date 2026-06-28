package com.chaos.payment.application;

import com.chaos.payment.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FraudDetectionService {

    private static final Logger LOG = Logger.getLogger(FraudDetectionService.class);

    @Inject
    FraudRuleEngine ruleEngine;

    @Inject
    FraudHistoryRepository historyRepository;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @Channel("fraud-alerts")
    Emitter<String> fraudAlertEmitter;

    @Inject
    ObjectMapper objectMapper;

    public FraudAnalysisResult analyze(FraudCheckRequest request) {
        LOG.infof("Analyzing fraud risk for transaction %s, user %s, amount %s",
                request.transactionId(), request.userId(), request.amount());

        FraudAnalysisResult result = ruleEngine.analyze(request);

        meterRegistry.counter("fraud.analysis.total",
                "verdict", result.getVerdict().name()).increment();
        meterRegistry.gauge("fraud.risk.score",
                result.getTotalRiskScore());

        historyRepository.recordTransaction(
                request.userId(),
                request.transactionId(),
                request.amount(),
                request.providerId());

        if (result.isBlocked() || result.requiresReview()) {
            publishFraudAlert(result);
        }

        LOG.infof("Fraud analysis complete for %s: verdict=%s, score=%d",
                request.transactionId(), result.getVerdict(), result.getTotalRiskScore());

        return result;
    }

    private void publishFraudAlert(FraudAnalysisResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            fraudAlertEmitter.send(json);
            LOG.warnf("Fraud alert published for transaction %s: %s",
                    result.getTransactionId(), result.getVerdict());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish fraud alert for transaction %s",
                    result.getTransactionId());
        }
    }
}
