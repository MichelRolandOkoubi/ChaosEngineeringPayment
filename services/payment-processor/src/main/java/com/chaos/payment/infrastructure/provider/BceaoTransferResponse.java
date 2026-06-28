package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class BceaoTransferResponse {
    private String codeRetour;
    private String libelleRetour;
    private String numeroOperation;
    private String dateOperation;
}
