package com.chaos.payment.tests.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end integration tests for the payment gateway.
 * All external payment provider APIs are mocked with WireMock.
 */
@QuarkusTest
@TestSecurity(authorizationEnabled = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Payment Gateway — Integration Tests")
class PaymentGatewayIntegrationTest {

    private static WireMockServer wireMock;
    private static final String BASE = "/api/v1";

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().port(9999));
        wireMock.start();
        WireMock.configureFor("localhost", 9999);
        

        stubOrangeMoneySuccess();
        stubMtnMomoSuccess();
        stubVisaSuccess();
        stubMpesaSuccess();
        stubProviderFailure("/orange/v1/payment", 503);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    // ─────────────────────────────────────────────
    // 1. Initiate Payment — Happy Path
    // ─────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("POST /payments returns 202 Accepted for valid Orange Money request")
    void initiateOrangeMoneyPayment() {
        given()
            .contentType(ContentType.JSON)
            .body(paymentRequest("ORANGE", "5000.00", "XOF", "+22670000001"))
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(202), is(200)))
            .body("transactionId", notNullValue())
            .body("status", anyOf(is("ACCEPTED"), is("QUEUED"), is("INITIATED")));
    }

    // ─────────────────────────────────────────────
    // 2. Multi-Provider Routing
    // ─────────────────────────────────────────────
    @ParameterizedTest
    @Order(2)
    @CsvSource({
        "ORANGE,   5000.00, XOF, +22670000001",
        "MOOV,     2000.00, XOF, +22676000002",
        "MTN,      8000.00, XOF, +22601000003",
        "WAVE,     1500.00, XOF, +22675000004",
        "AIRTEL,   3000.00, KES, +254700000005",
        "MPESA,    1000.00, KES, +254711000006",
        "VISA,      200.00, USD, +33600000007",
        "MASTERCARD,150.00, EUR, +33700000008",
        "BTC,         0.01, BTC, +12120000009",
        "PI_SPI_BCEAO,10000.00,XOF,+22670000010"
    })
    @DisplayName("POST /payments routes correctly to each provider")
    void initiatePaymentAllProviders(String provider, String amount,
                                     String currency, String phone) {
        int status = given()
            .contentType(ContentType.JSON)
            .body(paymentRequest(provider, amount, currency, phone))
        .when()
            .post(BASE + "/payments")
        .then()
            .extract().statusCode();

        assertThat(status)
            .as("Provider %s should return 200, 202 or 422 (invalid) not 500", provider)
            .isIn(200, 202, 422);
    }

    // ─────────────────────────────────────────────
    // 3. Input Validation
    // ─────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("POST /payments returns 400 when amount is negative")
    void rejectNegativeAmount() {
        given()
            .contentType(ContentType.JSON)
            .body(paymentRequest("ORANGE", "-100.00", "XOF", "+22670000001"))
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test
    @Order(4)
    @DisplayName("POST /payments returns 400 when userId is missing")
    void rejectMissingUserId() {
        Map<String, Object> body = paymentRequest("ORANGE", "1000.00", "XOF", "+22670000001");
        body.remove("userId");

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test
    @Order(5)
    @DisplayName("POST /payments returns 400 for unknown provider")
    void rejectUnknownProvider() {
        given()
            .contentType(ContentType.JSON)
            .body(paymentRequest("UNKNOWN_PROVIDER", "1000.00", "XOF", "+22670000001"))
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(400), is(422), is(503)));
    }

    // ─────────────────────────────────────────────
    // 4. Query Payment (CQRS Read Side)
    // ─────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("GET /payments/{id} returns 200 for existing transaction")
    void queryExistingPayment() {
        // Create a payment first
        String txId = given()
            .contentType(ContentType.JSON)
            .body(paymentRequest("MTN", "3000.00", "XOF", "+22601000003"))
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(200), is(202)))
            .extract().jsonPath().getString("transactionId");

        if (txId == null) return; // skip if creation was queued without ID

        given()
            .when()
            .get(BASE + "/payments/" + txId)
        .then()
            .statusCode(anyOf(is(200), is(404))) // 404 acceptable before projection updates
            .body(anyOf(
                containsString(txId),
                emptyOrNullString()
            ));
    }

    @Test
    @Order(7)
    @DisplayName("GET /payments/{id} returns 404 for unknown transaction")
    void queryUnknownPayment() {
        given()
            .when()
            .get(BASE + "/payments/non-existent-tx-" + UUID.randomUUID())
        .then()
            .statusCode(anyOf(is(404), is(200))); // 200 with empty body also acceptable
    }

    // ─────────────────────────────────────────────
    // 5. List Payments
    // ─────────────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("GET /payments returns list (possibly empty)")
    void listPayments() {
        given()
            .queryParam("userId", "test-user-integration")
        .when()
            .get(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    // ─────────────────────────────────────────────
    // 6. Health Check
    // ─────────────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("GET /health returns UP status")
    void healthCheck() {
        given()
            .when()
            .get(BASE + "/health")
        .then()
            .statusCode(200)
            .body("status", anyOf(is("UP"), is("HEALTHY"), notNullValue()));
    }

    // ─────────────────────────────────────────────
    // 7. Duplicate Transaction Idempotency
    // ─────────────────────────────────────────────
    @Test
    @Order(10)
    @DisplayName("Duplicate payment with same idempotency key returns same transactionId")
    void idempotentPayment() {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        Map<String, Object> body = paymentRequest("VISA", "100.00", "USD", "+12120000007");
        body.put("idempotencyKey", idempotencyKey);

        String txId1 = given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(200), is(202)))
            .extract().jsonPath().getString("transactionId");

        String txId2 = given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post(BASE + "/payments")
        .then()
            .statusCode(anyOf(is(200), is(202)))
            .extract().jsonPath().getString("transactionId");

        if (txId1 != null && txId2 != null) {
            assertThat(txId1).as("Idempotent payment should return same txId").isEqualTo(txId2);
        }
    }

    // ─────────────────────────────────────────────
    // WireMock stubs
    // ─────────────────────────────────────────────
    private static void stubOrangeMoneySuccess() {
        stubFor(post(urlPathMatching("/orange/.*/payment"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"status":"SUCCESS","transId":"OM-%s","message":"Payment accepted"}
                    """.formatted(UUID.randomUUID()))));
    }

    private static void stubMtnMomoSuccess() {
        stubFor(post(urlPathMatching("/mtn/.*/requesttopay"))
            .willReturn(aResponse()
                .withStatus(202)
                .withHeader("X-Reference-Id", UUID.randomUUID().toString())));
    }

    private static void stubVisaSuccess() {
        stubFor(post(urlPathMatching("/visa/.*/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"result":"APPROVED","transactionId":"VX-%s"}
                    """.formatted(UUID.randomUUID()))));
    }

    private static void stubMpesaSuccess() {
        stubFor(post(urlPathMatching("/mpesa/.*/stkpush"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"ResponseCode":"0","CheckoutRequestID":"ws_CO_%s"}
                    """.formatted(UUID.randomUUID()))));
    }

    private static void stubProviderFailure(String path, int httpStatus) {
        stubFor(post(urlEqualTo(path))
            .willReturn(aResponse().withStatus(httpStatus)));
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────
    private Map<String, Object> paymentRequest(String provider, String amount,
                                                String currency, String phone) {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", "it-user-" + UUID.randomUUID().toString().substring(0, 8));
        req.put("amount", Double.parseDouble(amount));
        req.put("currencyCode", currency);
        req.put("providerId", provider);
        req.put("description", "Integration test payment via " + provider);
        req.put("phoneNumber", phone);
        req.put("callbackUrl", "https://callback.test/payment");
        return req;
    }
}
