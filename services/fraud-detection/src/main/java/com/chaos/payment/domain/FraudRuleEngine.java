package com.chaos.payment.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FraudRuleEngine {

    private static final Logger LOG = Logger.getLogger(FraudRuleEngine.class);

    @ConfigProperty(name = "fraud.risk.block-threshold", defaultValue = "80")
    int blockThreshold;

    @ConfigProperty(name = "fraud.risk.review-threshold", defaultValue = "40")
    int reviewThreshold;

    @ConfigProperty(name = "fraud.amount.high-risk", defaultValue = "500000")
    BigDecimal highRiskAmount;

    @ConfigProperty(name = "fraud.velocity.max-per-minute", defaultValue = "5")
    int maxTransactionsPerMinute;

    @Inject
    FraudHistoryRepository historyRepository;

    public FraudAnalysisResult analyze(FraudCheckRequest request) {
        List<String> triggeredRules = new ArrayList<>();
        int totalRisk = 0;

        // Rule 1 — montant excessif
        if (request.amount().compareTo(highRiskAmount) > 0) {
            triggeredRules.add("AMOUNT_THRESHOLD");
            totalRisk += 30;
            LOG.debugf("High amount detected: %s for transaction %s",
                    request.amount(), request.transactionId());
        }

        // Rule 2 — vélocité élevée
        int recentCount = historyRepository.countRecentTransactions(request.userId(), 60);
        if (recentCount >= maxTransactionsPerMinute) {
            triggeredRules.add("VELOCITY_CHECK");
            totalRisk += 40;
            LOG.debugf("Velocity check triggered: %d transactions in 60s for user %s",
                    recentCount, request.userId());
        }

        // Rule 3 — doublon
        boolean isDuplicate = historyRepository.isDuplicateTransaction(
                request.userId(), request.amount(), request.providerId(), 300);
        if (isDuplicate) {
            triggeredRules.add("DUPLICATE_TRANSACTION");
            totalRisk += 50;
        }

        // Rule 4 — utilisateur blacklisté
        if (historyRepository.isBlacklisted(request.userId())) {
            triggeredRules.add("BLACKLISTED_USER");
            totalRisk += 100;
        }

        // Rule 5 — taux de retry élevé
        int retryCount = historyRepository.countRecentRetries(request.userId(), 3600);
        if (retryCount >= 5) {
            triggeredRules.add("HIGH_RETRY_RATE");
            totalRisk += 20;
        }

        FraudAnalysisResult.Verdict verdict;
        String reason;
        if (totalRisk >= blockThreshold) {
            verdict = FraudAnalysisResult.Verdict.BLOCKED;
            reason = "High fraud risk score: " + totalRisk + ". Rules triggered: " + triggeredRules;
        } else if (totalRisk >= reviewThreshold) {
            verdict = FraudAnalysisResult.Verdict.REVIEW;
            reason = "Medium fraud risk, manual review required. Score: " + totalRisk;
        } else {
            verdict = FraudAnalysisResult.Verdict.APPROVED;
            reason = null;
        }

        return FraudAnalysisResult.builder()
                .transactionId(request.transactionId())
                .userId(request.userId())
                .verdict(verdict)
                .totalRiskScore(totalRisk)
                .triggeredRules(triggeredRules)
                .reason(reason)
                .analyzedAt(java.time.Instant.now())
                .build();
    }
}
