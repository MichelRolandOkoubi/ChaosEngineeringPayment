package com.chaos.payment.interfaces.rest;

import com.chaos.payment.application.NotificationService;
import com.chaos.payment.domain.NotificationRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "Notification management")
public class NotificationResource {

    @Inject
    NotificationService notificationService;

    @POST
    @Path("/send")
    @Operation(summary = "Send a notification manually")
    public Response send(NotificationRequest request) {
        try {
            notificationService.notify(request);
            return Response.accepted(Map.of(
                    "message", "Notification dispatched",
                    "transactionId", request.transactionId()
            )).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/health")
    @Operation(summary = "Notification service health check")
    public Response health() {
        return Response.ok(Map.of("status", "UP", "service", "notification-service")).build();
    }
}
