package com.chaos.payment.infrastructure.channel;

import com.chaos.payment.domain.NotificationChannel;
import com.chaos.payment.domain.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@ApplicationScoped
public class PushNotificationChannel implements NotificationChannel {

    private static final Logger LOG = Logger.getLogger(PushNotificationChannel.class);

    @Inject
    SnsClient snsClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "notification.push.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "notification.sns.topic-arn", defaultValue = "")
    String topicArn;

    @Override
    public boolean supports(NotificationRequest request) {
        return enabled && topicArn != null && !topicArn.isBlank();
    }

    @Override
    public void send(NotificationRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                    "transactionId", request.transactionId(),
                    "userId", request.userId(),
                    "eventType", request.eventType(),
                    "status", request.status(),
                    "amount", request.amount().toPlainString(),
                    "currency", request.currencyCode()
            );

            String message = objectMapper.writeValueAsString(payload);

            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .subject("payment-notification")
                    .messageAttributes(Map.of(
                            "eventType", software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(request.eventType())
                                    .build()
                    ))
                    .build());

            LOG.infof("Push notification sent for transaction %s", request.transactionId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send push notification for transaction %s", request.transactionId());
        }
    }
}
