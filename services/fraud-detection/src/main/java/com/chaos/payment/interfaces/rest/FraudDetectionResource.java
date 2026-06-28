package com.chaos.payment.interfaces.rest;

import com.chaos.payment.application.FraudDetectionService;
import com.chaos.payment.domain.FraudAnalysisResult;
import com.chaos.payment.domain.FraudCheckRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/v1/fraud")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Fraud Detection", description = "Fraud analysis endpoints")
public class FraudDetectionResource {

    @Inject
    FraudDetectionService fraudDetectionService;

    @POST
    @Path("/analyze")
    @Operation(summary = "Analyze a transaction for fraud risk")
    public Response analyze(FraudCheckRequest request) {
        FraudAnalysisResult result = fraudDetectionService.analyze(request);
        int httpStatus = switch (result.getVerdict()) {
            case BLOCKED -> 403;
            case REVIEW -> 202;
            case APPROVED -> 200;
        };
        return Response.status(httpStatus).entity(result).build();
    }

    @GET
    @Path("/health")
    @Operation(summary = "Fraud detection service health check")
    public Response health() {
        return Response.ok(Map.of("status", "UP", "service", "fraud-detection")).build();
    }
}
