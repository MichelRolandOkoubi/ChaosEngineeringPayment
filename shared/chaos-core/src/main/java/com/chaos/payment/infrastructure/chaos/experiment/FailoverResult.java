package com.chaos.payment.infrastructure.chaos.experiment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FailoverResult {
    private boolean successful;
    private long failoverTimeMs;
}
