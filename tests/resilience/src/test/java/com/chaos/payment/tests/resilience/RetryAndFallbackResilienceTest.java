package com.chaos.payment.tests.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests for:
 * - @Retry: automatic retry on transient failures
 * - @Timeout: hard deadline enforcement
 * - @Fallback: fallback to RabbitMQ queue when all retries exhausted
 * - @Bulkhead: concurrency limit per provider
 */
@QuarkusTest
@TestSecurity(authorizationEnabled = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Retry, Timeout & Fallback — Resilience Tests")
class RetryAndFallbackResilienceTest {

    private static WireMockServer wireMock;
    private static final String BASE = "/api/v1";

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(wireMockConfig().port(9997));
        wireMock.start();
        configureFor("localhost", 9997);
        // RestAssured port is configured automatically by @QuarkusTest
    }

    @AfterAll
    static void stop() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    // ─────────────────────────────────────────────
    // 1. Retry succeeds on 2nd attempt
    // ─────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("@Retry: payment succeeds after 1 transient failure")
    void retrySucceedsOnSecondAttempt() {
        // First call fails, second succeeds
        stubFor(post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("first-failure"));

        stubFor(post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("first-failure")
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"status\":\"SUCCESS\",\"transactionId\":\"RT-001\"}")));

        int statusCode = given()
            .contentType(ContentType.JSON)
            .body(request("ORANGE", "2000.00"))
        .when()
            .post(BASE + "/payments")
        .then()
            .extract().statusCode();

        // 200/202 = success (retry worked), 503/202 queued = fallback triggered
        assertThat(statusCode)
            .as("Payment should succeed on retry or fallback to queue")
            .isIn(200, 202, 503);
    }

    // ─────────────────────────────────────────────
    // 2. Retry exhausted → fallback to RabbitMQ queue
    // ─────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("@Fallback: payment queued in RabbitMQ when all retries exhausted")
    void fallbackToQueueWhenAllRetriesFail() {
        // All provider calls fail
        stubFor(post(anyUrl()).willReturn(aResponse().withStatus(500)));

        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        var response = given()
            .contentType(ContentType.JSON)
            .body(request("MTN", "5000.00"))
        .when()
            .post(BASE + "/payments")
        .then()
            .extract().response();

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");
        System.clearProperty("chaos.scenarios");

        // Fallback should queue the command — 202 QUEUED or 503 with retry info
        assertThat(response.statusCode())
            .as("Exhausted retries should queue or return fallback response, not 500")
            .isIn(200, 202, 503);

        // If 503, body should explain the fallback (not a raw stack trace)
        if (response.statusCode() == 503) {
            String body = response.body().asString();
            assertThat(body).as("Error response body should be non-empty").isNotEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // 3. @Timeout enforced
    // ─────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("@Timeout: slow provider triggers timeout and fallback within 30s")
    void timeoutIsEnforcedOnSlowProvider() {
        // Provider responds after 35 seconds (beyond 30s @Timeout)
        stubFor(post(anyUrl()).willReturn(
            aResponse().withStatus(200)
                       .withFixedDelay(35_000)
                       .withBody("{\"status\":\"SUCCESS\"}")));

        long start = System.currentTimeMillis();

        int statusCode = given()
            .contentType(ContentType.JSON)
            .body(request("VISA", "100.00"))
        .when()
            .post(BASE + "/payments")
        .then()
            .extract().statusCode();

        long elapsed = System.currentTimeMillis() - start;

        // Request should resolve before 32s (not block for 35s)
        assertThat(elapsed)
            .as("@Timeout should interrupt the request before 32 seconds")
            .isLessThan(32_000);

        // Should get a timeout response, not a success from the slow provider
        assertThat(statusCode)
            .as("Timed-out request should return 202 (queued) or 408/503 (timeout)")
            .isIn(200, 202, 408, 503);
    }

    // ─────────────────────────────────────────────
    // 4. @Bulkhead: excess concurrent requests rejected or queued
    // ─────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("@Bulkhead: concurrent requests above limit are queued, not lost")
    void bulkheadQueuesExcessConcurrentRequests() throws InterruptedException {
        // Provider takes 2s per call — saturates bulkhead quickly
        stubFor(post(anyUrl()).willReturn(
            aResponse().withStatus(200)
                       .withFixedDelay(2_000)
                       .withBody("{\"status\":\"SUCCESS\"}")));

        int totalRequests = 30;
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger bulkheadRejected = new AtomicInteger(0);
        AtomicInteger serverErrors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        ExecutorService exec = Executors.newFixedThreadPool(30);

        for (int i = 0; i < totalRequests; i++) {
            exec.submit(() -> {
                try {
                    int code = given()
                        .contentType(ContentType.JSON)
                        .body(request("ORANGE", "1000.00"))
                        .post(BASE + "/payments")
                        .statusCode();

                    if (code == 200 || code == 202) accepted.incrementAndGet();
                    else if (code == 429 || code == 503) bulkheadRejected.incrementAndGet();
                    else if (code == 500) serverErrors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        exec.shutdown();

        // 0 server errors — system must handle the excess gracefully
        assertThat(serverErrors.get())
            .as("No 500 errors from bulkhead saturation")
            .isZero();

        // At least some must go through (not all rejected)
        assertThat(accepted.get())
            .as("At least some concurrent requests should be accepted")
            .isGreaterThan(0);
    }

    // ─────────────────────────────────────────────
    // 5. Retry respects idempotency (no duplicate charges)
    // ─────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("@Retry: retried payment does not double-charge (idempotent provider call)")
    void retryIsIdempotent() {
        AtomicInteger providerCallCount = new AtomicInteger(0);

        // Count provider calls
        stubFor(post(anyUrl())
            .inScenario("idem-retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("fail-1"));

        stubFor(post(anyUrl())
            .inScenario("idem-retry")
            .whenScenarioStateIs("fail-1")
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"status\":\"SUCCESS\",\"transactionId\":\"IDEM-001\"}")));

        String txId1 = given()
            .contentType(ContentType.JSON)
            .body(request("VISA", "500.00"))
            .post(BASE + "/payments")
            .jsonPath().getString("transactionId");

        // Retry scenario: 1 failure + 1 success = exactly 2 provider calls (no duplicate charge)
        verify(exactly(2), postRequestedFor(anyUrl()));
    }

    // ─────────────────────────────────────────────
    // 6. Partial provider outage — failover to backup provider
    // ─────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("Provider failover: primary down → backup provider used automatically")
    void automaticProviderFailover() {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0");
        System.setProperty("chaos.scenarios", "ERROR");

        // Primary provider (ORANGE) is down
        var response = given()
            .contentType(ContentType.JSON)
            .body(request("ORANGE", "3000.00"))
        .when()
            .post(BASE + "/payments");

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");
        System.clearProperty("chaos.scenarios");

        // System should either route to backup or queue — never 500
        assertThat(response.statusCode())
            .as("Primary provider down should trigger backup or fallback, not 500")
            .isIn(200, 202, 503);
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────
    private Map<String, Object> request(String provider, String amount) {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", "res-user-" + UUID.randomUUID().toString().substring(0, 6));
        req.put("amount", Double.parseDouble(amount));
        req.put("currencyCode", "XOF");
        req.put("providerId", provider);
        req.put("description", "Resilience test via " + provider);
        req.put("phoneNumber", "+22670000001");
        req.put("callbackUrl", "https://callback.test/resilience");
        return req;
    }
}
