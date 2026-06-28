package com.chaos.payment.infrastructure.chaos;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@RegisterForReflection
public class ChaosMonkey {

    private static final Logger LOG = Logger.getLogger(ChaosMonkey.class);

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "chaos.enabled", defaultValue = "false")
    boolean chaosEnabled;

    @ConfigProperty(name = "chaos.failure-rate", defaultValue = "0.1")
    double failureRate;

    @ConfigProperty(name = "chaos.latency.min-ms", defaultValue = "100")
    long minLatencyMs;

    @ConfigProperty(name = "chaos.latency.max-ms", defaultValue = "5000")
    long maxLatencyMs;

    @ConfigProperty(name = "chaos.scenarios", defaultValue = "LATENCY,ERROR,THROTTLE,NETWORK_PARTITION,REGION_FAILURE")
    String enabledScenarios;

    private final AtomicInteger chaosInjectionCount = new AtomicInteger(0);
    private final Map<String, Integer> providerFailureCount = new HashMap<>();
    private volatile ChaosScenario activeScenario;

    public enum ChaosScenario {
        LATENCY("Artificial latency injection"),
        ERROR("Service error injection"),
        THROTTLE("Request throttling"),
        NETWORK_PARTITION("Network partition simulation"),
        REGION_FAILURE("Region failure simulation"),
        DATA_CORRUPTION("Data corruption simulation"),
        MEMORY_PRESSURE("Memory pressure simulation"),
        CPU_SPIKE("CPU spike simulation"),
        PROVIDER_TIMEOUT("Payment provider timeout"),
        DUPLICATE_TRANSACTION("Duplicate transaction injection"),
        PARTIAL_FAILURE("Partial transaction failure");

        private final String description;

        ChaosScenario(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public void maybeInjectFailure(String providerId) {
        if (!chaosEnabled) return;

        double random = ThreadLocalRandom.current().nextDouble();
        if (random >= failureRate) return;

        ChaosScenario scenario = selectScenario();
        activeScenario = scenario;

        LOG.warnf("🔥 CHAOS: Injecting %s for provider: %s", scenario, providerId);
        meterRegistry.counter("chaos.injection",
                "scenario", scenario.name(),
                "provider", providerId).increment();
        chaosInjectionCount.incrementAndGet();
        providerFailureCount.merge(providerId, 1, Integer::sum);

        executeScenario(scenario, providerId);
    }

    public void maybeInjectDynamoDBChaos() {
        if (!chaosEnabled) return;

        double random = ThreadLocalRandom.current().nextDouble();
        if (random >= failureRate * 0.5) return;

        ChaosScenario scenario = ThreadLocalRandom.current().nextBoolean()
                ? ChaosScenario.LATENCY : ChaosScenario.ERROR;

        LOG.warnf("🔥 CHAOS: Injecting DynamoDB chaos: %s", scenario);
        meterRegistry.counter("chaos.dynamodb", "scenario", scenario.name()).increment();
        executeDatabaseScenario(scenario);
    }

    public void injectRegionFailure(String region) {
        LOG.errorf("🔥 CHAOS: Simulating region failure for: %s", region);
        meterRegistry.counter("chaos.region.failure", "region", region).increment();
        activeScenario = ChaosScenario.REGION_FAILURE;
        injectLatency(Duration.ofSeconds(30));
    }

    public void injectNetworkPartition(String serviceA, String serviceB) {
        LOG.errorf("🔥 CHAOS: Network partition between %s and %s", serviceA, serviceB);
        meterRegistry.counter("chaos.network.partition",
                "service_a", serviceA, "service_b", serviceB).increment();
        activeScenario = ChaosScenario.NETWORK_PARTITION;
        throw new NetworkPartitionException(
                "Simulated network partition between " + serviceA + " and " + serviceB);
    }

    public void injectDataCorruption(String transactionId) {
        LOG.errorf("🔥 CHAOS: Injecting data corruption for transaction: %s", transactionId);
        meterRegistry.counter("chaos.data.corruption").increment();
        throw new DataCorruptionException(
                "Simulated data corruption for transaction: " + transactionId);
    }

    public ChaosReport generateReport() {
        return ChaosReport.builder()
                .totalInjections(chaosInjectionCount.get())
                .providerFailureCounts(Map.copyOf(providerFailureCount))
                .activeScenario(activeScenario != null ? activeScenario.name() : "NONE")
                .chaosEnabled(chaosEnabled)
                .failureRate(failureRate)
                .build();
    }

    public boolean isChaosEnabled() { return chaosEnabled; }
    public int getTotalInjections() { return chaosInjectionCount.get(); }
    public ChaosScenario getActiveScenario() { return activeScenario; }

    private void executeScenario(ChaosScenario scenario, String providerId) {
        switch (scenario) {
            case LATENCY -> injectLatency(Duration.ofMillis(
                    ThreadLocalRandom.current().nextLong(minLatencyMs, maxLatencyMs)));
            case ERROR -> throw new ChaosException(
                    "Chaos: Simulated service error for provider: " + providerId);
            case THROTTLE -> injectThrottle();
            case PROVIDER_TIMEOUT -> injectLatency(Duration.ofSeconds(30));
            case DUPLICATE_TRANSACTION -> throw new DuplicateTransactionException(
                    "Chaos: Simulated duplicate transaction");
            case PARTIAL_FAILURE -> injectPartialFailure(providerId);
            default -> LOG.warnf("Chaos scenario %s — monitoring only", scenario);
        }
    }

    private void executeDatabaseScenario(ChaosScenario scenario) {
        switch (scenario) {
            case LATENCY -> injectLatency(Duration.ofMillis(
                    ThreadLocalRandom.current().nextLong(500, 3000)));
            case ERROR -> throw new ChaosException("Chaos: DynamoDB connection failed");
            default -> LOG.warnf("DB Chaos scenario %s active", scenario);
        }
    }

    private void injectLatency(Duration duration) {
        try {
            LOG.infof("🔥 CHAOS: Injecting latency: %d ms", duration.toMillis());
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectThrottle() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(500, 2000));
            throw new ThrottleException("Chaos: Service throttled — too many requests");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectPartialFailure(String providerId) {
        if (ThreadLocalRandom.current().nextBoolean()) {
            throw new ChaosException("Chaos: Partial failure for provider: " + providerId);
        }
    }

    private ChaosScenario selectScenario() {
        List<String> scenarios = Arrays.asList(enabledScenarios.split(","));
        String selected = scenarios.get(
                ThreadLocalRandom.current().nextInt(scenarios.size()));
        return ChaosScenario.valueOf(selected.trim());
    }
}
