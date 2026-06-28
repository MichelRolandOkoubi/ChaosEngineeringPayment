package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BceaoTransferRequest {
    private BigDecimal montant;
    private String devise;
    private String compteDebiteur;
    private String compteCrediteur;
    private String motif;
    private String reference;
    private String codeOperateur;
}
