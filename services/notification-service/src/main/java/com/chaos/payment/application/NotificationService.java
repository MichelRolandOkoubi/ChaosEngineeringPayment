package com.chaos.payment.application;

import com.chaos.payment.domain.NotificationChannel;
import com.chaos.payment.domain.NotificationRequest;
import com.chaos.payment.infrastructure.channel.EmailNotificationChannel;
import com.chaos.payment.infrastructure.channel.PushNotificationChannel;
import com.chaos.payment.infrastructure.channel.SmsNotificationChannel;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    @Inject
    SmsNotificationChannel smsChannel;

    @Inject
    EmailNotificationChannel emailChannel;

    @Inject
    PushNotificationChannel pushChannel;

    @Inject
    MeterRegistry meterRegistry;

    public void notify(NotificationRequest request) {
        LOG.infof("Processing notification for transaction %s, event %s",
                request.transactionId(), request.eventType());

        List<NotificationChannel> channels = List.of(smsChannel, emailChannel, pushChannel);
        int sent = 0;

        for (NotificationChannel channel : channels) {
            if (channel.supports(request)) {
                try {
                    channel.send(request);
                    sent++;
                    meterRegistry.counter("notification.sent",
                            "channel", channel.getClass().getSimpleName(),
                            "event", request.eventType()).increment();
                } catch (Exception e) {
                    LOG.warnf(e, "Channel %s failed for transaction %s",
                            channel.getClass().getSimpleName(), request.transactionId());
                    meterRegistry.counter("notification.failed",
                            "channel", channel.getClass().getSimpleName()).increment();
                }
            }
        }

        if (sent == 0) {
            LOG.debugf("No notification channel available for transaction %s (phoneNumber=%s, email=%s)",
                    request.transactionId(), request.phoneNumber(), request.email());
        }
    }
}
