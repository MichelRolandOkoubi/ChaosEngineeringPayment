package com.chaos.payment.application.command;
import lombok.Value;
@Value
public class PaymentCommandResult {
    String transactionId;
    String status;
    String message;

    public static PaymentCommandResult success(String transactionId) {
        return new PaymentCommandResult(transactionId, "SUCCESS", null);
    }
    public static PaymentCommandResult rejected(String reason) {
        return new PaymentCommandResult(null, "REJECTED", reason);
    }
    public static PaymentCommandResult queued(String message) {
        return new PaymentCommandResult(null, "QUEUED", message);
    }
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    public boolean isQueued() {
        return "QUEUED".equals(status);
    }
}