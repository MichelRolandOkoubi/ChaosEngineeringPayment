package com.chaos.payment.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudAnalysisResult {

    public enum Verdict {
        APPROVED, REVIEW, BLOCKED
    }

    private final String transactionId;
    private final String userId;
    private final Verdict verdict;
    private final int totalRiskScore;
    private final List<String> triggeredRules;
    private final String reason;
    private final Instant analyzedAt;

    public boolean isBlocked() {
        return Verdict.BLOCKED.equals(verdict);
    }

    public boolean requiresReview() {
        return Verdict.REVIEW.equals(verdict);
    }

    public static FraudAnalysisResult approved(String transactionId, String userId) {
        return FraudAnalysisResult.builder()
                .transactionId(transactionId)
                .userId(userId)
                .verdict(Verdict.APPROVED)
                .totalRiskScore(0)
                .triggeredRules(List.of())
                .analyzedAt(Instant.now())
                .build();
    }
}
