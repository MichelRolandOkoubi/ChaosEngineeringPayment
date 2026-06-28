package com.chaos.payment.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WaveCheckoutResponse {
    private String id;
    @JsonProperty("wave_launch_url")
    private String waveLaunchUrl;
    private String status;
    @JsonProperty("client_reference")
    private String clientReference;
}
