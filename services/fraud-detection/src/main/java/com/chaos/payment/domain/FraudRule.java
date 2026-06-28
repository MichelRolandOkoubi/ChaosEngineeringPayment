package com.chaos.payment.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@RegisterForReflection
public class FraudRule {

    public enum RuleType {
        VELOCITY_CHECK,        // trop de transactions en peu de temps
        AMOUNT_THRESHOLD,      // montant anormalement élevé
        GEOLOCATION_MISMATCH,  // pays/région incohérent
        DUPLICATE_TRANSACTION, // doublon suspect
        BLACKLISTED_USER,      // utilisateur bloqué
        UNUSUAL_PROVIDER,      // fournisseur inhabituel pour cet utilisateur
        HIGH_RETRY_RATE        // taux de retry élevé = signe de fraude
    }

    private final String ruleId;
    private final RuleType type;
    private final String description;
    private final BigDecimal threshold;
    private final int windowSeconds;
    private final int maxCount;
    private final boolean enabled;
    private final int riskScore;
}
