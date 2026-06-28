package com.chaos.payment.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MPesaSTKPushRequest {
    @JsonProperty("BusinessShortCode") private String businessShortCode;
    @JsonProperty("Password") private String password;
    @JsonProperty("Timestamp") private String timestamp;
    @JsonProperty("TransactionType") private String transactionType;
    @JsonProperty("Amount") private long amount;
    @JsonProperty("PartyA") private String partyA;
    @JsonProperty("PartyB") private String partyB;
    @JsonProperty("PhoneNumber") private String phoneNumber;
    @JsonProperty("CallBackURL") private String callBackURL;
    @JsonProperty("AccountReference") private String accountReference;
    @JsonProperty("TransactionDesc") private String transactionDesc;
}
