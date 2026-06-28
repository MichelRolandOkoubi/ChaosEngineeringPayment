package com.chaos.payment.domain;

public interface NotificationChannel {
    boolean supports(NotificationRequest request);
    void send(NotificationRequest request);
}
