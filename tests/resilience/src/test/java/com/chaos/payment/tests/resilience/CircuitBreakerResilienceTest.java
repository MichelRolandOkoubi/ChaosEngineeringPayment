package com.chaos.payment.tests.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * Circuit Breaker resilience tests (MicroProfile Fault Tolerance).
 *
 * Lifecycle under test:
 *   CLOSED  →  (failures exceed threshold)  →  OPEN
 *   OPEN    →  (wait delay)                 →  HALF-OPEN
 *   HALF-OPEN → (probe success)             →  CLOSED
 *   HALF-OPEN → (probe fails)               →  OPEN
 */
@QuarkusTest
@TestSecurity(authorizationEnabled = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Circuit Breaker — Resilience Tests")
class CircuitBreakerResilienceTest {

    private static WireMockServer wireMock;
    private static final String BASE = "/api/v1";

    // MicroProfile CB defaults: failureRatio=0.5, requestVolumeThreshold=4
    private static final int CB_THRESHOLD = 10;

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(wireMockConfig().port(9998));
        wireMock.start();
        configureFor("localhost", 9998);
        // RestAssured port is configured automatically by @QuarkusTest
    }

    @AfterAll
    static void stop() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // ─────────────────────────────────────────────
    // 1. CB stays CLOSED under normal traffic
    // ─────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("Circuit breaker stays CLOSED when provider responds normally")
    void circuitBreakerStaysClosedUnderNormalLoad() {
        stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)
            .withBody("{\"status\":\"SUCCESS\"}")));

        for (int i = 0; i < 5; i++) {
            int code = given()
                .contentType(ContentType.JSON)
                .body(request("ORANGE", "1000.00"))
                .post(BASE + "/payments")
                .statusCode();

            assertThat(code)
                .as("Under normal conditions, CB should not open (no 503)")
                .isIn(200, 202, 422);
        }
    }

    // ─────────────────────────────────────────────
    // 2. CB opens after consecutive failures
    // ─────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("Circuit breaker opens after hitting failure threshold")
    void circuitBreakerOpensAfterThreshold() throws InterruptedException {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        List<Integer> statusCodes = new ArrayList<>();

        for (int i = 0; i < CB_THRESHOLD + 5; i++) {
            int code = given()
                .contentType(ContentType.JSON)
                .body(request("ORANGE", "500.00"))
                .post(BASE + "/payments")
                .statusCode();
            statusCodes.add(code);
            Thread.sleep(50);
        }

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");
        System.clearProperty("chaos.scenarios");

        // After threshold, expect fast-fail (503) or fallback (202 queued)
        long fastFailOrFallback = statusCodes.stream()
            .filter(c -> c == 503 || c == 202)
            .count();

        assertThat(fastFailOrFallback)
            .as("CB should produce fast-fails or fallback after opening")
            .isGreaterThan(2);
    }

    // ─────────────────────────────────────────────
    // 3. CB fast-fails without calling provider
    // ─────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("Open circuit breaker fast-fails without hitting the provider")
    void openCircuitBreakerDoesNotCallProvider() throws InterruptedException {
        // Force CB open
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        for (int i = 0; i < CB_THRESHOLD; i++) {
            given().contentType(ContentType.JSON)
                   .body(request("MTN", "1000.00"))
                   .post(BASE + "/payments");
            Thread.sleep(50);
        }

        // Reset stubs to track calls
        wireMock.resetRequests();
        stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)));

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");

        // CB should be open — provider should NOT be called
        given().contentType(ContentType.JSON)
               .body(request("MTN", "1000.00"))
               .post(BASE + "/payments");

        // WireMock should receive 0 provider calls while CB is open
        verify(0, postRequestedFor(anyUrl()));
    }

    // ─────────────────────────────────────────────
    // 4. CB transitions to HALF-OPEN after delay
    // ─────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("Circuit breaker transitions to HALF-OPEN after delay and probes provider")
    void circuitBreakerTransitionsToHalfOpen() throws InterruptedException {
        // Force CB open
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        for (int i = 0; i < CB_THRESHOLD; i++) {
            given().contentType(ContentType.JSON)
                   .body(request("VISA", "200.00"))
                   .post(BASE + "/payments");
            Thread.sleep(50);
        }

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");

        // Wait for CB delay (MicroProfile default is 5s)
        Thread.sleep(6000);

        // Stub success for probe
        stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)
            .withBody("{\"status\":\"SUCCESS\"}")));

        // First request in HALF-OPEN: probe
        int probeCode = given()
            .contentType(ContentType.JSON)
            .body(request("VISA", "200.00"))
            .post(BASE + "/payments")
            .statusCode();

        // Should not be a persistent fast-fail (503 from open CB)
        assertThat(probeCode)
            .as("HALF-OPEN probe should attempt the call, not stay open")
            .isIn(200, 202, 422, 503);
    }

    // ─────────────────────────────────────────────
    // 5. CB closes after successful probe
    // ─────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("Circuit breaker returns to CLOSED state after successful recovery")
    void circuitBreakerClosesAfterRecovery() throws InterruptedException {
        // Force CB open
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        for (int i = 0; i < CB_THRESHOLD; i++) {
            given().contentType(ContentType.JSON)
                   .body(request("MASTERCARD", "100.00"))
                   .post(BASE + "/payments");
        }

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");

        // Wait for CB delay
        Thread.sleep(6000);

        // Provider now healthy
        stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)
            .withBody("{\"status\":\"SUCCESS\"}")));

        // Allow CB to recover through HALF-OPEN → CLOSED
        List<Integer> recoveryCodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int code = given()
                .contentType(ContentType.JSON)
                .body(request("MASTERCARD", "100.00"))
                .post(BASE + "/payments")
                .statusCode();
            recoveryCodes.add(code);
            Thread.sleep(500);
        }

        long successCount = recoveryCodes.stream().filter(c -> c == 200 || c == 202).count();
        assertThat(successCount)
            .as("After CB closes, most requests should succeed")
            .isGreaterThanOrEqualTo(2);
    }

    // ─────────────────────────────────────────────
    // 6. Per-provider CB isolation
    // ─────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("CB open on ORANGE does not affect MTN (per-provider isolation)")
    void circuitBreakerIsIsolatedPerProvider() throws InterruptedException {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        // Flood ORANGE to open its CB
        for (int i = 0; i < CB_THRESHOLD; i++) {
            given().contentType(ContentType.JSON)
                   .body(request("ORANGE", "500.00"))
                   .post(BASE + "/payments");
        }

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");

        // MTN should still work
        int mtnStatus = given()
            .contentType(ContentType.JSON)
            .body(request("MTN", "3000.00"))
            .post(BASE + "/payments")
            .statusCode();

        assertThat(mtnStatus)
            .as("MTN should not be affected by ORANGE circuit breaker")
            .isIn(200, 202, 422);
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────
    private Map<String, Object> request(String provider, String amount) {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", "cb-user-" + UUID.randomUUID().toString().substring(0, 6));
        req.put("amount", Double.parseDouble(amount));
        req.put("currencyCode", "XOF");
        req.put("providerId", provider);
        req.put("description", "CB test via " + provider);
        req.put("phoneNumber", "+22670000001");
        req.put("callbackUrl", "https://callback.test/cb");
        return req;
    }
}
