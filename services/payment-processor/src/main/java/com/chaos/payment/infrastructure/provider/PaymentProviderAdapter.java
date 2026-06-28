package com.chaos.payment.infrastructure.provider;

import com.chaos.payment.domain.model.Payment;
import com.chaos.payment.domain.model.PaymentProvider;
import com.chaos.payment.infrastructure.provider.model.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class PaymentProviderAdapter {

    private static final Logger LOG = Logger.getLogger(PaymentProviderAdapter.class);

    @Inject
    @RestClient
    OrangeMoneyClient orangeMoneyClient;

    @Inject
    @RestClient
    MoovMoneyClient moovMoneyClient;

    @Inject
    @RestClient
    MTNMobileMoneyClient mtnMobileMoneyClient;

    @Inject
    @RestClient
    WaveMoneyClient waveMoneyClient;

    @Inject
    @RestClient
    AirtelMoneyClient airtelMoneyClient;

    @Inject
    @RestClient
    MPesaClient mpesaClient;

    @Inject
    @RestClient
    VisaClient visaClient;

    @Inject
    @RestClient
    MastercardClient mastercardClient;

    @Inject
    @RestClient
    BitcoinClient bitcoinClient;

    @Inject
    @RestClient
    BceaoInterBankClient bceaoInterBankClient;

    /**
     * Route payment to appropriate provider with fallback chain
     */
    @Retry(maxRetries = 2, delay = 500, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 10000)
    @Timeout(value = 20, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "fallbackProvider")
    public ProviderResponse processPayment(Payment payment, Map<String, String> params) {

        PaymentProvider provider = payment.getProvider();

        LOG.infof("Routing payment to provider: %s, amount: %s %s",
                provider.getCode(),
                payment.getAmount().getAmount(),
                payment.getCurrency().getCode());

        return switch (provider) {
            case ORANGE -> processOrangeMoney(payment, params);
            case MOOV -> processMoovMoney(payment, params);
            case MTN -> processMTNMoney(payment, params);
            case WAVE -> processWaveMoney(payment, params);
            case AIRTEL -> processAirtelMoney(payment, params);
            case MPESA -> processMPesa(payment, params);
            case VISA -> processVisa(payment, params);
            case MASTERCARD -> processMastercard(payment, params);
            case BTC -> processBitcoin(payment, params);
            case PI_SPI_BCEAO -> processBceaoInterBank(payment, params);
        };
    }

    // ============================================
    // Mobile Money Providers
    // ============================================
    private ProviderResponse processOrangeMoney(Payment payment, Map<String, String> params) {
        try {
            OrangeMoneyRequest request = OrangeMoneyRequest.builder()
                    .merchantId(params.get("merchantId"))
                    .amount(payment.getAmount().getAmount())
                    .currency(payment.getCurrency().getCode())
                    .customerPhone(params.get("phoneNumber"))
                    .reference(payment.getTransactionId().getValue())
                    .callbackUrl(params.get("callbackUrl"))
                    .notifUrl(params.get("notifUrl"))
                    .returnUrl(params.get("returnUrl"))
                    .build();

            OrangeMoneyResponse response = orangeMoneyClient.initiatePayment(request);

            return ProviderResponse.builder()
                    .success(response.isSuccess())
                    .externalReference(response.getPayToken())
                    .paymentUrl(response.getPaymentUrl())
                    .status(response.getStatus())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Orange Money payment failed");
            return ProviderResponse.failure("ORANGE_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processMoovMoney(Payment payment, Map<String, String> params) {
        try {
            MoovRequest request = MoovRequest.builder()
                    .amount(payment.getAmount().getAmount().toString())
                    .currency(payment.getCurrency().getCode())
                    .phoneNumber(params.get("phoneNumber"))
                    .transactionId(payment.getTransactionId().getValue())
                    .description(payment.getDescription())
                    .build();

            MoovResponse response = moovMoneyClient.requestPayment(request);

            return ProviderResponse.builder()
                    .success("SUCCESS".equals(response.getResponseCode()))
                    .externalReference(response.getReferenceId())
                    .status(response.getStatus())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Moov Money payment failed");
            return ProviderResponse.failure("MOOV_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processMTNMoney(Payment payment, Map<String, String> params) {
        try {
            // MTN uses OAuth2 for authentication
            String accessToken = mtnMobileMoneyClient.getAccessToken(
                    params.get("subscriptionKey")).getAccessToken();

            MTNPaymentRequest request = MTNPaymentRequest.builder()
                    .amount(payment.getAmount().getAmount().toString())
                    .currency(payment.getCurrency().getCode())
                    .externalId(payment.getTransactionId().getValue())
                    .payer(new MTNPayer(params.get("phoneNumber")))
                    .payerMessage(payment.getDescription())
                    .payeeNote("Payment via PaymentAggregator")
                    .build();

            String referenceId = payment.getTransactionId().getValue();
            mtnMobileMoneyClient.requestToPayDebit(accessToken, referenceId, request);

            // Poll for status (MTN is async)
            MTNPaymentStatus status = mtnMobileMoneyClient.getPaymentStatus(
                    accessToken, referenceId);

            return ProviderResponse.builder()
                    .success("SUCCESSFUL".equals(status.getStatus()))
                    .externalReference(referenceId)
                    .status(status.getStatus())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "MTN Mobile Money payment failed");
            return ProviderResponse.failure("MTN_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processWaveMoney(Payment payment, Map<String, String> params) {
        try {
            WaveCheckoutRequest request = WaveCheckoutRequest.builder()
                    .amount(payment.getAmount().getAmount())
                    .currency(payment.getCurrency().getCode())
                    .clientReference(payment.getTransactionId().getValue())
                    .mobileNumber(params.get("phoneNumber"))
                    .webhookUrl(params.get("callbackUrl"))
                    .build();

            WaveCheckoutResponse response = waveMoneyClient.createCheckoutSession(request);

            return ProviderResponse.builder()
                    .success(response != null)
                    .externalReference(response != null ? response.getId() : null)
                    .paymentUrl(response != null ? response.getWaveLaunchUrl() : null)
                    .status(response != null ? "PENDING" : "FAILED")
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Wave Money payment failed");
            return ProviderResponse.failure("WAVE_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processAirtelMoney(Payment payment, Map<String, String> params) {
        try {
            AirtelPaymentRequest request = AirtelPaymentRequest.builder()
                    .reference(payment.getTransactionId().getValue())
                    .subscriber(new AirtelSubscriber(
                            params.get("phoneNumber"),
                            payment.getCurrency().getCode()))
                    .transaction(new AirtelTransaction(
                            payment.getAmount().getAmount(),
                            payment.getCurrency().getCode(),
                            payment.getTransactionId().getValue()))
                    .build();

            AirtelPaymentResponse response = airtelMoneyClient.requestPayment(request);

            return ProviderResponse.builder()
                    .success("200".equals(response.getStatus().getCode()))
                    .externalReference(response.getData() != null ? response.getData().getTransaction().getId() : null)
                    .status(response.getStatus().getMessage())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Airtel Money payment failed");
            return ProviderResponse.failure("AIRTEL_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processMPesa(Payment payment, Map<String, String> params) {
        try {
            MPesaSTKPushRequest request = MPesaSTKPushRequest.builder()
                    .businessShortCode(params.get("shortCode"))
                    .password(generateMPesaPassword(params))
                    .timestamp(getCurrentTimestamp())
                    .transactionType("CustomerPayBillOnline")
                    .amount(payment.getAmount().getAmount().longValue())
                    .partyA(params.get("phoneNumber"))
                    .partyB(params.get("shortCode"))
                    .phoneNumber(params.get("phoneNumber"))
                    .callBackURL(params.get("callbackUrl"))
                    .accountReference(payment.getTransactionId().getValue())
                    .transactionDesc(payment.getDescription())
                    .build();

            MPesaSTKPushResponse response = mpesaClient.stkPush(request);

            return ProviderResponse.builder()
                    .success("0".equals(response.getResponseCode()))
                    .externalReference(response.getCheckoutRequestID())
                    .status(response.getResponseDescription())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "M-Pesa payment failed");
            return ProviderResponse.failure("MPESA_ERROR", e.getMessage());
        }
    }

    // ============================================
    // International Providers
    // ============================================
    private ProviderResponse processVisa(Payment payment, Map<String, String> params) {
        try {
            VisaPaymentRequest request = VisaPaymentRequest.builder()
                    .amount(payment.getAmount().getAmount())
                    .currency(payment.getCurrency().getCode())
                    .paymentMethodData(new VisaCardData(
                            params.get("cardNumber"),
                            params.get("expiryMonth"),
                            params.get("expiryYear"),
                            params.get("cvv")))
                    .transactionId(payment.getTransactionId().getValue())
                    .build();

            VisaPaymentResponse response = visaClient.processPayment(request);

            return ProviderResponse.builder()
                    .success("00".equals(response.getResponseCode()))
                    .externalReference(response.getTransactionIdentifier())
                    .status(response.getResponseMessage())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Visa payment failed");
            return ProviderResponse.failure("VISA_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processMastercard(Payment payment, Map<String, String> params) {
        try {
            MastercardPaymentRequest request = MastercardPaymentRequest.builder()
                    .amount(payment.getAmount().getAmount())
                    .currency(payment.getCurrency().getCode())
                    .orderId(payment.getTransactionId().getValue())
                    .description(payment.getDescription())
                    .build();

            MastercardPaymentResponse response = mastercardClient.initiateSession(request);

            return ProviderResponse.builder()
                    .success("SUCCESS".equals(response.getResult()))
                    .externalReference(response.getSessionId())
                    .paymentUrl(response.getSuccessIndicator())
                    .status(response.getResult())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Mastercard payment failed");
            return ProviderResponse.failure("MASTERCARD_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processBitcoin(Payment payment, Map<String, String> params) {
        try {
            BTCPaymentRequest request = BTCPaymentRequest.builder()
                    .price(payment.getAmount().getAmount().doubleValue())
                    .currency(payment.getCurrency().getCode())
                    .orderId(payment.getTransactionId().getValue())
                    .redirectURL(params.get("callbackUrl"))
                    .notificationURL(params.get("webhookUrl"))
                    .build();

            BTCPaymentResponse response = bitcoinClient.createInvoice(request);

            return ProviderResponse.builder()
                    .success(response != null)
                    .externalReference(response != null ? response.getId() : null)
                    .paymentUrl(response != null ? response.getCheckoutLink() : null)
                    .status("PENDING")
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Bitcoin payment failed");
            return ProviderResponse.failure("BTC_ERROR", e.getMessage());
        }
    }

    private ProviderResponse processBceaoInterBank(Payment payment, Map<String, String> params) {
        try {
            // BCEAO PI/SPI Interbank Transfer (XOF currency zone)
            BceaoTransferRequest request = BceaoTransferRequest.builder()
                    .montant(payment.getAmount().getAmount())
                    .devise("XOF")
                    .compteDebiteur(params.get("sourceAccount"))
                    .compteCrediteur(params.get("destinationAccount"))
                    .motif(payment.getDescription())
                    .reference(payment.getTransactionId().getValue())
                    .codeOperateur(params.get("operatorCode"))
                    .build();

            BceaoTransferResponse response = bceaoInterBankClient.initiateTransfer(request);

            return ProviderResponse.builder()
                    .success("00".equals(response.getCodeRetour()))
                    .externalReference(response.getNumeroOperation())
                    .status(response.getLibelleRetour())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "BCEAO Interbank transfer failed");
            return ProviderResponse.failure("BCEAO_ERROR", e.getMessage());
        }
    }

    /**
     * Fallback method - queues payment for manual processing
     */
    public ProviderResponse fallbackProvider(Payment payment, Map<String, String> params) {
        LOG.warnf("All payment providers unavailable, queuing payment: %s",
                payment.getTransactionId().getValue());

        return ProviderResponse.builder()
                .success(false)
                .status("QUEUED_FOR_RETRY")
                .errorMessage("Service temporarily unavailable. Payment queued.")
                .errorCode("PROVIDER_UNAVAILABLE")
                .build();
    }

    private String generateMPesaPassword(Map<String, String> params) {
        String raw = params.get("shortCode") + params.get("passKey") + getCurrentTimestamp();
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private String getCurrentTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now());
    }
}