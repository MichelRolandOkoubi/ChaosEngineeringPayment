package com.chaos.payment.infrastructure.chaos;

import com.chaos.payment.infrastructure.chaos.experiment.*;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class ChaosOrchestrator {

    private static final Logger LOG = Logger.getLogger(ChaosOrchestrator.class);

    @Inject
    ChaosMonkey chaosMonkey;

    @Inject
    ResilienceValidator resilienceValidator;

    @Inject
    FailoverTestService failoverTestService;

    private final List<ChaosExperiment> registeredExperiments = new CopyOnWriteArrayList<>();
    private final List<ChaosExperimentResult> experimentResults = new CopyOnWriteArrayList<>();

    public Uni<ChaosExperimentResult> runProviderFailureExperiment(String provider) {
        ChaosExperiment experiment = ChaosExperiment.builder()
                .id(UUID.randomUUID().toString())
                .name("Provider Failure: " + provider)
                .hypothesis("System should automatically failover to backup provider")
                .steady_state(Map.of(
                        "payment_success_rate", ">95%",
                        "latency_p99", "<2000ms",
                        "error_rate", "<5%"))
                .build();

        return Uni.createFrom().item(() -> {
            LOG.infof("🧪 Starting chaos experiment: %s", experiment.getName());

            SteadyStateMetrics initial = resilienceValidator.captureMetrics();
            chaosMonkey.injectRegionFailure("us-east-1-" + provider);

            sleep(5_000);
            SteadyStateMetrics during = resilienceValidator.captureMetrics();
            sleep(10_000);
            SteadyStateMetrics after = resilienceValidator.captureMetrics();

            ChaosExperimentResult result = ChaosExperimentResult.builder()
                    .experimentId(experiment.getId())
                    .name(experiment.getName())
                    .initialState(initial)
                    .duringState(during)
                    .recoveredState(after)
                    .passed(validateSteadyState(after))
                    .build();

            experimentResults.add(result);
            LOG.infof("🧪 Experiment completed: %s — %s",
                    experiment.getName(), result.isPassed() ? "PASSED ✅" : "FAILED ❌");
            return result;
        });
    }

    public Uni<ChaosExperimentResult> runDatabasePartitionExperiment() {
        return Uni.createFrom().item(() -> {
            LOG.infof("🧪 Starting database partition experiment");

            SteadyStateMetrics initial = resilienceValidator.captureMetrics();
            chaosMonkey.maybeInjectDynamoDBChaos();
            SteadyStateMetrics during = resilienceValidator.captureMetrics();

            ChaosExperimentResult result = ChaosExperimentResult.builder()
                    .experimentId(UUID.randomUUID().toString())
                    .name("DynamoDB Partition Test")
                    .initialState(initial)
                    .duringState(during)
                    .passed(resilienceValidator.checkDataIntegrity())
                    .build();

            experimentResults.add(result);
            return result;
        });
    }

    public Uni<ChaosExperimentResult> runMultiRegionFailoverExperiment() {
        return failoverTestService.testRegionFailover()
                .onItem().transform(failoverResult -> {
                    ChaosExperimentResult result = ChaosExperimentResult.builder()
                            .experimentId(UUID.randomUUID().toString())
                            .name("Multi-Region Failover Test")
                            .passed(failoverResult.isSuccessful())
                            .failoverTime(failoverResult.getFailoverTimeMs())
                            .build();
                    experimentResults.add(result);
                    return result;
                });
    }

    public Uni<ChaosExperimentResult> runCascadeFailureExperiment() {
        return Uni.createFrom().item(() -> {
            LOG.warnf("🔥 Starting cascade failure experiment — ALL PROVIDERS");

            SteadyStateMetrics initial = resilienceValidator.captureMetrics();
            List.of("ORANGE", "MOOV", "MTN", "WAVE").forEach(provider -> {
                try {
                    chaosMonkey.maybeInjectFailure(provider);
                } catch (Exception e) {
                    LOG.warnf("Chaos injected for %s: %s", provider, e.getMessage());
                }
            });

            SteadyStateMetrics during = resilienceValidator.captureMetrics();

            return ChaosExperimentResult.builder()
                    .experimentId(UUID.randomUUID().toString())
                    .name("Cascade Failure Test")
                    .initialState(initial)
                    .duringState(during)
                    .passed(during.getSuccessRate() > 0.5)
                    .build();
        });
    }

    @Scheduled(every = "1h", delayed = "5m")
    public void scheduledChaosExperiment() {
        if (!chaosMonkey.isChaosEnabled()) return;

        LOG.infof("⚡ Running scheduled chaos experiment");
        List<String> providers = List.of(
                "ORANGE", "MOOV", "MTN", "WAVE", "AIRTEL", "MPESA",
                "VISA", "MASTERCARD", "BTC", "PI_SPI_BCEAO");

        String randomProvider = providers.get(new Random().nextInt(providers.size()));
        runProviderFailureExperiment(randomProvider)
                .subscribe().with(
                        result -> LOG.infof("Scheduled chaos result: %s", result),
                        error -> LOG.errorf(error, "Scheduled chaos failed"));
    }

    public List<ChaosExperimentResult> getResults() {
        return Collections.unmodifiableList(experimentResults);
    }

    private boolean validateSteadyState(SteadyStateMetrics metrics) {
        return metrics.getSuccessRate() > 0.95
                && metrics.getP99LatencyMs() < 2000
                && metrics.getErrorRate() < 0.05;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
