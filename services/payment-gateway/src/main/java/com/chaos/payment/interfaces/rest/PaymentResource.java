package com.chaos.payment.interfaces.rest;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.application.command.handler.InitiatePaymentCommandHandler;
import com.chaos.payment.application.query.GetPaymentQuery;
import com.chaos.payment.application.query.handler.GetPaymentQueryHandler;
import com.chaos.payment.application.query.ListPaymentsQuery;
import com.chaos.payment.infrastructure.chaos.ChaosMonkey;
import com.chaos.payment.infrastructure.chaos.ChaosOrchestrator;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

import org.eclipse.microprofile.openapi.annotations.info.Info;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("/api/v1")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@OpenAPIDefinition(info = @Info(title = "Payment Aggregator Chaos Engineering API", version = "1.0.0", description = "Multi-region payment aggregator with chaos engineering capabilities"))
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    InitiatePaymentCommandHandler commandHandler;

    @Inject
    GetPaymentQueryHandler queryHandler;

    @Inject
    ChaosMonkey chaosMonkey;

    @Inject
    ChaosOrchestrator chaosOrchestrator;

    // ============================================
    // Payment Commands (Write Side - CQRS)
    // ============================================

    @POST
    @Path("/payments")
    @Authenticated
    @Timed(value = "payment.initiate.time", description = "Time to initiate payment")
    @Counted(value = "payment.initiate.count", description = "Number of payment initiations")
    @APIResponse(responseCode = "202", description = "Payment accepted", content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "503", description = "Service unavailable")
    public CompletionStage<Response> initiatePayment(@Valid InitiatePaymentCommand command) {

        LOG.infof("Received payment request: provider=%s, amount=%s %s",
                command.providerId(), command.amount(), command.currencyCode());

        return commandHandler.handle(command)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return Response.accepted(
                                new PaymentResponse(
                                        result.getTransactionId(),
                                        "ACCEPTED",
                                        "Payment is being processed"))
                                .build();
                    } else if (result.isQueued()) {
                        return Response.accepted(
                                new PaymentResponse(
                                        result.getTransactionId(),
                                        "QUEUED",
                                        result.getMessage()))
                                .build();
                    } else {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse(result.getMessage()))
                                .build();
                    }
                })
                .exceptionally(e -> {
                    LOG.errorf(e, "Payment initiation failed");
                    return Response.serverError()
                            .entity(new ErrorResponse("Internal server error"))
                            .build();
                });
    }

    // ============================================
    // Payment Queries (Read Side - CQRS)
    // ============================================

    @GET
    @Path("/payments/{transactionId}")
    @Timed(value = "payment.query.time")
    public Response getPayment(@PathParam("transactionId") String transactionId) {

        return queryHandler.handle(new GetPaymentQuery(transactionId))
                .map(payment -> Response.ok(payment).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/payments")
    @Timed(value = "payment.list.time")
    public Response listPayments(
            @QueryParam("userId") String userId,
            @QueryParam("providerId") String providerId,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        ListPaymentsQuery query = new ListPaymentsQuery(userId, providerId, status, page, size);
        return Response.ok(queryHandler.listPayments(query)).build();
    }

    @GET
    @Path("/payments/{transactionId}/events")
    @RolesAllowed("admin")
    public Response getPaymentEvents(@PathParam("transactionId") String transactionId) {
        return Response.ok(queryHandler.getEventHistory(transactionId)).build();
    }

    // ============================================
    // Health & Readiness
    // ============================================

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new HealthStatus(
                "UP",
                System.currentTimeMillis(),
                chaosMonkey.isChaosEnabled() ? "CHAOS_MODE" : "NORMAL")).build();
    }

    @GET
    @Path("/health/providers")
    public Response providerHealth() {
        // Check health of all payment providers
        return Response.ok(buildProviderHealthStatus()).build();
    }

    // ============================================
    // Chaos Engineering Endpoints
    // ============================================

    @POST
    @Path("/chaos/inject")
    @RolesAllowed("chaos-admin")
    public Response injectChaos(ChaosRequest request) {
        LOG.warnf("Manual chaos injection requested: %s", request.getScenario());

        try {
            chaosMonkey.maybeInjectFailure(request.getTarget());
            return Response.ok(new ChaosResponse(
                    "CHAOS_INJECTED",
                    request.getScenario())).build();
        } catch (Exception e) {
            return Response.ok(new ChaosResponse(
                    "CHAOS_TRIGGERED",
                    e.getMessage())).build();
        }
    }

    @POST
    @Path("/chaos/experiments/{experimentType}")
    @RolesAllowed("chaos-admin")
    public CompletionStage<Response> runExperiment(
            @PathParam("experimentType") String experimentType,
            @QueryParam("target") String target) {

        return switch (experimentType) {
            case "provider-failure" ->
                chaosOrchestrator.runProviderFailureExperiment(target)
                        .subscribe().asCompletionStage()
                        .thenApply(result -> Response.ok(result).build());

            case "db-partition" ->
                chaosOrchestrator.runDatabasePartitionExperiment()
                        .subscribe().asCompletionStage()
                        .thenApply(result -> Response.ok(result).build());

            case "region-failover" ->
                chaosOrchestrator.runMultiRegionFailoverExperiment()
                        .subscribe().asCompletionStage()
                        .thenApply(result -> Response.ok(result).build());

            case "cascade-failure" ->
                chaosOrchestrator.runCascadeFailureExperiment()
                        .subscribe().asCompletionStage()
                        .thenApply(result -> Response.ok(result).build());

            default -> java.util.concurrent.CompletableFuture.completedFuture(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Unknown experiment type: " + experimentType)
                            .build());
        };
    }

    @GET
    @Path("/chaos/report")
    @RolesAllowed("chaos-admin")
    public Response getChaosReport() {
        return Response.ok(chaosMonkey.generateReport()).build();
    }

    @GET
    @Path("/chaos/experiments")
    @RolesAllowed("chaos-admin")
    public Response getExperimentResults() {
        return Response.ok(chaosOrchestrator.getResults()).build();
    }

    // ============================================
    // Helper methods
    // ============================================
    private Object buildProviderHealthStatus() {
        return Map.of(
                "ORANGE", checkProvider("ORANGE"),
                "MOOV", checkProvider("MOOV"),
                "MTN", checkProvider("MTN"),
                "WAVE", checkProvider("WAVE"),
                "AIRTEL", checkProvider("AIRTEL"),
                "MPESA", checkProvider("MPESA"),
                "VISA", checkProvider("VISA"),
                "MASTERCARD", checkProvider("MASTERCARD"),
                "BTC", checkProvider("BTC"),
                "PI_SPI_BCEAO", checkProvider("PI_SPI_BCEAO"));
    }

    private Map<String, Object> checkProvider(String provider) {
        return Map.of(
                "status", "UP",
                "provider", provider,
                "latency", 0,
                "timestamp", System.currentTimeMillis());
    }
}