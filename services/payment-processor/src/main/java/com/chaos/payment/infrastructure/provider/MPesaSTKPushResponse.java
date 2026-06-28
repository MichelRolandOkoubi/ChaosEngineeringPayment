package com.chaos.payment.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MPesaSTKPushResponse {
    @JsonProperty("MerchantRequestID") private String merchantRequestID;
    @JsonProperty("CheckoutRequestID") private String checkoutRequestID;
    @JsonProperty("ResponseCode") private String responseCode;
    @JsonProperty("ResponseDescription") private String responseDescription;
    @JsonProperty("CustomerMessage") private String customerMessage;
}
