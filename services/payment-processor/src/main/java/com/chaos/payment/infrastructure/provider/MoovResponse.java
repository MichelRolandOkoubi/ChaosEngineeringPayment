package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class MoovResponse {
    private String responseCode;
    private String referenceId;
    private String status;
    private String message;
}
