package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class AirtelPaymentResponse {
    private AirtelStatus status;
    private AirtelData data;

    @Data
    public static class AirtelStatus {
        private String code;
        private String message;
        private String resultCode;
    }

    @Data
    public static class AirtelData {
        private AirtelDataTransaction transaction;
    }

    @Data
    public static class AirtelDataTransaction {
        private String id;
        private String status;
        private String message;
    }
}
