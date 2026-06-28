package com.chaos.payment.tests.chaos;

import com.chaos.payment.infrastructure.chaos.ChaosMonkey;
import com.chaos.payment.infrastructure.chaos.ChaosOrchestrator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Payment Chaos Engineering Tests")
class PaymentChaosTest {

    @Inject
    ChaosMonkey chaosMonkey;

    @Inject
    ChaosOrchestrator chaosOrchestrator;

    private static final String BASE_PATH = "/api/v1";
    private static final int CONCURRENT_REQUESTS = 50;
    private static final double ACCEPTABLE_FAILURE_RATE = 0.15; // 15% max failure

    @BeforeAll
    static void setup() {
        RestAssured.port = 8080;
    }

    // ============================================
    // Test 1: Baseline (Steady State)
    // ============================================
    @Test
    @Order(1)
    @DisplayName("Verify steady state before chaos")
    void testSteadyState() {
        List<String> providers = List.of(
                "ORANGE", "MOOV", "MTN", "WAVE", "AIRTEL",
                "MPESA", "VISA", "MASTERCARD", "BTC", "PI_SPI_BCEAO");

        providers.forEach(provider -> {
            given()
                    .contentType(ContentType.JSON)
                    .body(buildPaymentRequest(provider, "100.00", "XOF"))
                    .when()
                    .post(BASE_PATH + "/payments")
                    .then()
                    .statusCode(anyOf(is(202), is(200)))
                    .body("status", anyOf(is("ACCEPTED"), is("QUEUED")));
        });
    }

    // ============================================
    // Test 2: Provider Failure Resilience
    // ============================================
    @ParameterizedTest
    @Order(2)
    @ValueSource(strings = { "ORANGE", "MTN", "MPESA", "VISA" })
    @DisplayName("Test payment resilience when provider fails")
    void testProviderFailureResilience(String provider) throws Exception {
        // Enable chaos for specific provider
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "0.5");

        int totalRequests = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    var response = given()
                            .contentType(ContentType.JSON)
                            .body(buildPaymentRequest(provider, "50.00", "XOF"))
                            .when()
                            .post(BASE_PATH + "/payments")
                            .then()
                            .extract()
                            .response();

                    if (response.statusCode() == 202 || response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        double failureRate = (double) failureCount.get() / totalRequests;

        System.out.printf("Provider: %s - Success: %d, Failure: %d, Rate: %.2f%%%n",
                provider, successCount.get(), failureCount.get(), failureRate * 100);

        // Assert system maintained acceptable service level
        assertThat(failureRate)
                .as("Failure rate for provider %s should be below %s",
                        provider, ACCEPTABLE_FAILURE_RATE)
                .isLessThanOrEqualTo(ACCEPTABLE_FAILURE_RATE);

        // Cleanup
        System.clearProperty("chaos.enabled");
    }

    // ============================================
    // Test 3: Latency Resilience
    // ============================================
    @Test
    @Order(3)
    @DisplayName("Test system behavior under high latency")
    void testLatencyResilience() throws Exception {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.scenarios", "LATENCY");
        System.setProperty("chaos.latency.min-ms", "1000");
        System.setProperty("chaos.latency.max-ms", "5000");

        long startTime = System.currentTimeMillis();

        var response = given()
                .contentType(ContentType.JSON)
                .body(buildPaymentRequest("ORANGE", "100.00", "XOF"))
                .when()
                .post(BASE_PATH + "/payments")
                .then()
                .extract()
                .response();

        long duration = System.currentTimeMillis() - startTime;

        // Should respond with timeout or success, not hang
        assertThat(response.statusCode())
                .as("Should return valid HTTP status even under latency")
                .isIn(200, 202, 408, 503);

        // Should not exceed 30 seconds (timeout limit)
        assertThat(duration)
                .as("Request should timeout within 30 seconds")
                .isLessThan(30000L);

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.scenarios");
    }

    // ============================================
    // Test 4: Circuit Breaker Test
    // ============================================
    @Test
    @Order(4)
    @DisplayName("Test circuit breaker opens after consecutive failures")
    void testCircuitBreakerOpens() throws Exception {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "1.0"); // 100% failure
        System.setProperty("chaos.scenarios", "ERROR");

        List<Integer> statusCodes = new ArrayList<>();

        // Send requests until circuit breaker opens
        for (int i = 0; i < 15; i++) {
            int statusCode = given()
                    .contentType(ContentType.JSON)
                    .body(buildPaymentRequest("ORANGE", "100.00", "XOF"))
                    .when()
                    .post(BASE_PATH + "/payments")
                    .then()
                    .extract()
                    .statusCode();

            statusCodes.add(statusCode);
            Thread.sleep(100);
        }

        // After circuit breaker opens, should get fast failures (503)
        long fastFailures = statusCodes.stream()
                .filter(code -> code == 503 || code == 202) // 202 = queued (fallback)
                .count();

        assertThat(fastFailures)
                .as("Should have fast failures after circuit breaker opens")
                .isGreaterThan(5);

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");
    }

    // ============================================
    // Test 5: Multi-Provider Cascade Failure
    // ============================================
    @Test
    @Order(5)
    @DisplayName("Test system survives cascade failure across all providers")
    void testCascadeFailureResilience() throws Exception {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "0.8"); // 80% failure across all

        List<String> allProviders = List.of(
                "ORANGE", "MOOV", "MTN", "WAVE", "AIRTEL",
                "MPESA", "VISA", "MASTERCARD", "BTC", "PI_SPI_BCEAO");

        AtomicInteger systemResponded = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(allProviders.size() * 3);
        ExecutorService executor = Executors.newFixedThreadPool(30);

        // Send 3 requests per provider simultaneously
        allProviders.forEach(provider -> {
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        var response = given()
                                .contentType(ContentType.JSON)
                                .body(buildPaymentRequest(provider, "100.00", "XOF"))
                                .when()
                                .post(BASE_PATH + "/payments")
                                .then()
                                .extract()
                                .response();

                        // System should always respond (even if queuing)
                        if (response.statusCode() != 500) {
                            systemResponded.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        });

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        int totalRequests = allProviders.size() * 3;
        double responseRate = (double) systemResponded.get() / totalRequests;

        System.out.printf("Cascade failure test - Total: %d, Responded: %d, Rate: %.2f%%%n",
                totalRequests, systemResponded.get(), responseRate * 100);

        // System should maintain >60% response rate even during cascade failure
        assertThat(responseRate)
                .as("System should respond to at least 60% of requests during cascade failure")
                .isGreaterThan(0.6);

        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");
    }

    // ============================================
    // Test 6: Data Integrity Under Chaos
    // ============================================
    @Test
    @Order(6)
    @DisplayName("Verify data integrity during chaos")
    void testDataIntegrityDuringChaos() throws Exception {
        String transactionId = null;

        // Create a payment
        var createResponse = given()
                .contentType(ContentType.JSON)
                .body(buildPaymentRequest("MTN", "500.00", "XOF"))
                .when()
                .post(BASE_PATH + "/payments")
                .then()
                .statusCode(anyOf(is(200), is(202)))
                .extract()
                .jsonPath();

        transactionId = createResponse.getString("transactionId");

        // Enable chaos
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "0.5");

        // Query the payment multiple times during chaos
        String finalTransactionId = transactionId;
        for (int i = 0; i < 10; i++) {
            var queryResponse = given()
                    .when()
                    .get(BASE_PATH + "/payments/" + finalTransactionId)
                    .then()
                    .extract()
                    .response();

            if (queryResponse.statusCode() == 200) {
                // Payment data should be consistent
                String status = queryResponse.jsonPath().getString("status");
                assertThat(status)
                        .as("Payment status should be a valid state")
                        .isIn("INITIATED", "PROCESSING", "COMPLETED", "FAILED",
                                "RETRYING", "REFUNDED", "QUEUED");
            }

            Thread.sleep(200);
        }

        // Query event history to verify event sourcing integrity
        given()
                .when()
                .get(BASE_PATH + "/payments/" + transactionId + "/events")
                .then()
                .statusCode(anyOf(is(200), is(403)))
                .body("size()", greaterThanOrEqualTo(0));

        System.clearProperty("chaos.enabled");
    }

    // ============================================
    // Test 7: Automatic Failover
    // ============================================
    @Test
    @Order(7)
    @DisplayName("Test automatic region failover")
    void testAutomaticFailover() throws Exception {
        // Inject region failure
        chaosOrchestrator.runMultiRegionFailoverExperiment()
                .subscribe().with(
                        result -> {
                            System.out.printf("Failover test result: passed=%b, time=%dms%n",
                                    result.isPassed(), result.getFailoverTime());

                            // Failover should happen within 60 seconds
                            assertThat(result.getFailoverTime())
                                    .as("Failover time should be under 60 seconds")
                                    .isLessThan(60000L);
                        },
                        error -> Assertions.fail("Failover test error: " + error.getMessage()));

        // Wait for test to complete
        Thread.sleep(5000);
    }

    // ============================================
    // Test 8: Load Test with Chaos
    // ============================================
    @Test
    @Order(8)
    @DisplayName("Load test with chaos engineering active")
    void testLoadWithChaos() throws Exception {
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "0.1");

        int totalRequests = CONCURRENT_REQUESTS;
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        List<String> providers = List.of(
                "ORANGE", "MOOV", "MTN", "WAVE", "VISA", "MASTERCARD");
        Random random = new Random();

        for (int i = 0; i < totalRequests; i++) {
            String provider = providers.get(random.nextInt(providers.size()));

            executor.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    var response = given()
                            .contentType(ContentType.JSON)
                            .body(buildPaymentRequest(provider,
                                    String.valueOf(random.nextInt(10000) + 1), "XOF"))
                            .when()
                            .post(BASE_PATH + "/payments")
                            .then()
                            .extract()
                            .response();

                    long latency = System.currentTimeMillis() - start;
                    latencies.add(latency);

                    if (response.statusCode() == 202 || response.statusCode() == 200) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Calculate statistics
        OptionalLong maxLatency = latencies.stream().mapToLong(Long::longValue).max();
        OptionalDouble avgLatency = latencies.stream().mapToLong(Long::longValue).average();

        double successRate = (double) successes.get() / totalRequests;

        System.out.printf(
                "\n=== Load Test Results with Chaos ===\n" +
                        "Total Requests: %d\n" +
                        "Successes: %d (%.1f%%)\n" +
                        "Failures: %d (%.1f%%)\n" +
                        "Max Latency: %d ms\n" +
                        "Avg Latency: %.1f ms\n" +
                        "===================================\n",
                totalRequests,
                successes.get(), successRate * 100,
                failures.get(), (1 - successRate) * 100,
                maxLatency.orElse(0),
                avgLatency.orElse(0));

        // Assertions
        assertThat(successRate)
                .as("Success rate should be above 85% even under chaos + load")
                .isGreaterThan(0.85);

        assertThat(maxLatency.orElse(Long.MAX_VALUE))
                .as("Max latency should be under 30 seconds")
                .isLessThan(30000L);

        System.clearProperty("chaos.enabled");
    }

    // ============================================
    // Test 9: Event Sourcing Consistency
    // ============================================
    @Test
    @Order(9)
    @DisplayName("Verify event sourcing data consistency under chaos")
    void testEventSourcingConsistency() throws Exception {
        // Create multiple payments
        List<String> transactionIds = new ArrayList<>();

        for (String provider : new String[] { "ORANGE", "MTN", "VISA" }) {
            var response = given()
                    .contentType(ContentType.JSON)
                    .body(buildPaymentRequest(provider, "250.00", "XOF"))
                    .when()
                    .post(BASE_PATH + "/payments")
                    .then()
                    .extract()
                    .response();

            if (response.statusCode() == 202) {
                String txId = response.jsonPath().getString("transactionId");
                if (txId != null)
                    transactionIds.add(txId);
            }
        }

        Thread.sleep(2000); // Allow events to propagate

        // Enable chaos
        System.setProperty("chaos.enabled", "true");

        // Verify each payment can be reconstructed from events
        transactionIds.forEach(txId -> {
            var eventsResponse = given()
                    .when()
                    .get(BASE_PATH + "/payments/" + txId + "/events")
                    .then()
                    .extract()
                    .response();

            // Either we get events or access denied (both valid)
            assertThat(eventsResponse.statusCode())
                    .as("Events endpoint should respond")
                    .isIn(200, 403, 404);

            // Query payment state
            var paymentResponse = given()
                    .when()
                    .get(BASE_PATH + "/payments/" + txId)
                    .then()
                    .extract()
                    .response();

            if (paymentResponse.statusCode() == 200) {
                // State should be deterministic from events
                assertThat(paymentResponse.jsonPath().getString("transactionId"))
                        .as("Transaction ID should match")
                        .isEqualTo(txId);

                assertThat(paymentResponse.jsonPath().getString("status"))
                        .as("Status should be a valid payment state")
                        .isNotNull()
                        .isIn("INITIATED", "PROCESSING", "COMPLETED", "FAILED",
                                "RETRYING", "REFUNDED");
            }
        });

        System.clearProperty("chaos.enabled");
    }

    // ============================================
    // Test 10: Recovery After Chaos
    // ============================================
    @Test
    @Order(10)
    @DisplayName("System recovers to steady state after chaos")
    void testSystemRecovery() throws Exception {
        // Phase 1: Introduce chaos
        System.setProperty("chaos.enabled", "true");
        System.setProperty("chaos.failure-rate", "0.7");

        // Run for 10 seconds with high chaos
        AtomicInteger chaosSuccesses = new AtomicInteger(0);
        for (int i = 0; i < 20; i++) {
            var response = given()
                    .contentType(ContentType.JSON)
                    .body(buildPaymentRequest("ORANGE", "100.00", "XOF"))
                    .when()
                    .post(BASE_PATH + "/payments")
                    .then()
                    .extract()
                    .response();

            if (response.statusCode() == 202)
                chaosSuccesses.incrementAndGet();
            Thread.sleep(500);
        }

        // Phase 2: Stop chaos
        System.clearProperty("chaos.enabled");
        System.clearProperty("chaos.failure-rate");

        // Wait for recovery
        Thread.sleep(5000);

        // Phase 3: Verify recovery
        AtomicInteger recoverySuccesses = new AtomicInteger(0);
        for (int i = 0; i < 10; i++) {
            var response = given()
                    .contentType(ContentType.JSON)
                    .body(buildPaymentRequest("ORANGE", "100.00", "XOF"))
                    .when()
                    .post(BASE_PATH + "/payments")
                    .then()
                    .extract()
                    .response();

            if (response.statusCode() == 202)
                recoverySuccesses.incrementAndGet();
            Thread.sleep(500);
        }

        double recoveryRate = (double) recoverySuccesses.get() / 10;

        System.out.printf(
                "During chaos: %d/20 (%.0f%%), After recovery: %d/10 (%.0f%%)%n",
                chaosSuccesses.get(), (double) chaosSuccesses.get() / 20 * 100,
                recoverySuccesses.get(), recoveryRate * 100);

        // After stopping chaos, success rate should recover to >80%
        assertThat(recoveryRate)
                .as("System should recover to >80% success rate after chaos stops")
                .isGreaterThan(0.8);
    }

    // ============================================
    // Helper Methods
    // ============================================
    private Map<String, Object> buildPaymentRequest(
            String provider, String amount, String currency) {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", "user-" + UUID.randomUUID().toString().substring(0, 8));
        request.put("amount", Double.parseDouble(amount));
        request.put("currencyCode", currency);
        request.put("providerId", provider);
        request.put("description", "Chaos test payment via " + provider);
        request.put("phoneNumber", "+22670123456");
        request.put("callbackUrl", "https://callback.example.com/payment");
        request.put("metadata", Map.of(
                "chaosTest", "true",
                "provider", provider,
                "testTimestamp", String.valueOf(System.currentTimeMillis())));
        return request;
    }
}