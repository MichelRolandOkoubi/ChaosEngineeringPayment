package com.chaos.payment.interfaces.rest;

import com.chaos.payment.application.PaymentCommandResult;
import com.chaos.payment.application.PaymentProcessingService;
import com.chaos.payment.application.ProcessPaymentCommand;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("/api/v1/processor")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment Processor", description = "Payment processing operations")
public class PaymentProcessorResource {

    private static final Logger LOG = Logger.getLogger(PaymentProcessorResource.class);

    @Inject
    PaymentProcessingService processingService;

    @Inject
    MeterRegistry meterRegistry;

    @POST
    @Path("/process")
    @Operation(summary = "Process a payment command synchronously")
    public Response processPayment(ProcessPaymentCommand command) {
        try {
            CompletableFuture<PaymentCommandResult> future = processingService.process(command);
            PaymentCommandResult result = future.get();
            return result.success()
                    ? Response.ok(result).build()
                    : Response.status(422).entity(result).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process payment %s", command.transactionId());
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/health")
    @Operation(summary = "Processor health check")
    public Response health() {
        return Response.ok(Map.of(
                "status", "UP",
                "service", "payment-processor",
                "metrics", Map.of(
                        "processed", meterRegistry.counter("payment.processor.success").count(),
                        "failed", meterRegistry.counter("payment.processor.failure").count()
                )
        )).build();
    }
}
