package com.chaos.payment.interfaces.rest;

import com.chaos.payment.application.query.*;
import com.chaos.payment.infrastructure.persistence.PaymentProjectionRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Payment Query", description = "Read-only payment queries (CQRS read side)")
public class PaymentQueryResource {

    @Inject
    PaymentProjectionRepository repository;

    @GET
    @Path("/{transactionId}")
    @Operation(summary = "Get payment by transaction ID")
    public Response getPayment(
            @PathParam("transactionId")
            @Parameter(description = "Payment transaction ID") String transactionId) {

        return repository.findById(transactionId)
                .map(view -> Response.ok(view).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Payment not found: " + transactionId))
                        .build());
    }

    @GET
    @Operation(summary = "List payments with optional filters")
    public Response listPayments(
            @QueryParam("userId") String userId,
            @QueryParam("providerId") String providerId,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        ListPaymentsQuery query = new ListPaymentsQuery(userId, providerId, status, page, size);
        List<PaymentView> payments = repository.findByQuery(query);

        return Response.ok(Map.of(
                "payments", payments,
                "page", page,
                "size", size,
                "count", payments.size()
        )).build();
    }

    @GET
    @Path("/health")
    @Operation(summary = "Query service health")
    public Response health() {
        return Response.ok(Map.of("status", "UP", "service", "payment-query")).build();
    }
}
