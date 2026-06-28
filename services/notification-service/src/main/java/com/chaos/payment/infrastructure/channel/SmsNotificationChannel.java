package com.chaos.payment.infrastructure.channel;

import com.chaos.payment.domain.NotificationChannel;
import com.chaos.payment.domain.NotificationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Base64;

@ApplicationScoped
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger LOG = Logger.getLogger(SmsNotificationChannel.class);

    @ConfigProperty(name = "notification.sms.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "notification.twilio.account-sid", defaultValue = "")
    String accountSid;

    @ConfigProperty(name = "notification.twilio.auth-token", defaultValue = "")
    String authToken;

    @ConfigProperty(name = "notification.twilio.from-number", defaultValue = "")
    String fromNumber;

    @Override
    public boolean supports(NotificationRequest request) {
        return enabled && request.phoneNumber() != null && !request.phoneNumber().isBlank();
    }

    @Override
    public void send(NotificationRequest request) {
        try {
            String message = buildSmsMessage(request);
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            Form form = new Form()
                    .param("To", request.phoneNumber())
                    .param("From", fromNumber)
                    .param("Body", message);

            Client client = ClientBuilder.newClient();
            String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes());

            client.target(url)
                    .request(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Basic " + credentials)
                    .post(Entity.form(form));

            LOG.infof("SMS sent for transaction %s to %s", request.transactionId(), request.phoneNumber());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send SMS for transaction %s", request.transactionId());
        }
    }

    private String buildSmsMessage(NotificationRequest request) {
        return switch (request.eventType()) {
            case "PaymentCompletedEvent" ->
                    String.format("Paiement confirmé: %s %s via %s. Ref: %s",
                            request.amount(), request.currencyCode(),
                            request.providerId(), request.transactionId());
            case "PaymentFailedEvent" ->
                    String.format("Paiement échoué: %s %s via %s. Raison: %s",
                            request.amount(), request.currencyCode(),
                            request.providerId(), request.reason() != null ? request.reason() : "Erreur technique");
            case "PaymentRefundedEvent" ->
                    String.format("Remboursement en cours: %s %s. Ref: %s",
                            request.amount(), request.currencyCode(), request.transactionId());
            default ->
                    String.format("Mise à jour paiement %s: statut %s",
                            request.transactionId(), request.status());
        };
    }
}
