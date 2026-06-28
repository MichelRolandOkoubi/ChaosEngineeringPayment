package com.chaos.payment.infrastructure.channel;

import com.chaos.payment.domain.NotificationChannel;
import com.chaos.payment.domain.NotificationRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Template;
import io.quarkus.qute.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger LOG = Logger.getLogger(EmailNotificationChannel.class);

    @Inject
    Mailer mailer;

    @Inject
    @Location("payment-notification")
    Template paymentTemplate;

    @ConfigProperty(name = "notification.email.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "notification.email.from", defaultValue = "noreply@payment.chaos")
    String fromAddress;

    @Override
    public boolean supports(NotificationRequest request) {
        return enabled && request.email() != null && !request.email().isBlank();
    }

    @Override
    public void send(NotificationRequest request) {
        try {
            String subject = buildSubject(request);
            String body = paymentTemplate
                    .data("transactionId", request.transactionId())
                    .data("amount", request.amount())
                    .data("currency", request.currencyCode())
                    .data("provider", request.providerId())
                    .data("status", request.status())
                    .data("eventType", request.eventType())
                    .data("reason", request.reason())
                    .render();

            mailer.send(Mail.withHtml(request.email(), subject, body).setFrom(fromAddress));

            LOG.infof("Email sent for transaction %s to %s", request.transactionId(), request.email());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send email for transaction %s", request.transactionId());
        }
    }

    private String buildSubject(NotificationRequest request) {
        return switch (request.eventType()) {
            case "PaymentCompletedEvent" -> "✓ Paiement confirmé - " + request.transactionId();
            case "PaymentFailedEvent" -> "✗ Paiement échoué - " + request.transactionId();
            case "PaymentRefundedEvent" -> "↩ Remboursement - " + request.transactionId();
            default -> "Notification paiement - " + request.transactionId();
        };
    }
}
