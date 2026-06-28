package com.chaos.payment.infrastructure.service;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.domain.service.FraudAnalysis;
import com.chaos.payment.domain.service.FraudDetectionService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Set;

@ApplicationScoped
public class DefaultFraudDetectionService implements FraudDetectionService {

    private static final Logger LOG = Logger.getLogger(DefaultFraudDetectionService.class);

    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("500000");
    private static final Set<String> BLOCKED_USER_PATTERNS = Set.of("test-fraud", "blocked-user");

    @Inject
    MeterRegistry meterRegistry;

    @Override
    public FraudAnalysis analyze(Object command) {
        if (!(command instanceof InitiatePaymentCommand cmd)) {
            return new FraudAnalysis(false, null);
        }

        // Rule 1: Amount above high-risk threshold
        if (cmd.amount() != null && cmd.amount().compareTo(HIGH_RISK_THRESHOLD) > 0) {
            LOG.warnf("Fraud rule triggered: amount %s exceeds threshold for user %s",
                    cmd.amount(), cmd.userId());
            meterRegistry.counter("fraud.detection", "rule", "high_amount").increment();
            return new FraudAnalysis(true, "Amount exceeds high-risk threshold: " + cmd.amount());
        }

        // Rule 2: Blocked user pattern
        if (cmd.userId() != null) {
            for (String pattern : BLOCKED_USER_PATTERNS) {
                if (cmd.userId().contains(pattern)) {
                    LOG.warnf("Fraud rule triggered: blocked user pattern for user %s", cmd.userId());
                    meterRegistry.counter("fraud.detection", "rule", "blocked_user").increment();
                    return new FraudAnalysis(true, "User ID matches blocked pattern");
                }
            }
        }

        // Rule 3: Missing phone number for mobile money providers
        Set<String> mobileMoney = Set.of("ORANGE", "MOOV", "MTN", "WAVE", "AIRTEL", "MPESA");
        if (mobileMoney.contains(cmd.providerId()) && (cmd.phoneNumber() == null || cmd.phoneNumber().isBlank())) {
            meterRegistry.counter("fraud.detection", "rule", "missing_phone").increment();
            return new FraudAnalysis(true, "Phone number required for mobile money provider: " + cmd.providerId());
        }

        return new FraudAnalysis(false, null);
    }
}
