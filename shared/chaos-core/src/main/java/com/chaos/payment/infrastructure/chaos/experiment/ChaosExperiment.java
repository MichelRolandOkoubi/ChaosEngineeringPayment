package com.chaos.payment.infrastructure.chaos.experiment;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ChaosExperiment {
    String id;
    String name;
    String hypothesis;
    Map<String, String> steady_state;
}
