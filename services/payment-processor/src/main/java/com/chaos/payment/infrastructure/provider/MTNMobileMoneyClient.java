package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "mtn-momo-client")
@Path("/collection/v1_0")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MTNMobileMoneyClient {
    @POST
    @Path("/token/")
    MTNAccessToken getAccessToken(@HeaderParam("Ocp-Apim-Subscription-Key") String subscriptionKey);

    @POST
    @Path("/requesttopay")
    void requestToPayDebit(
            @HeaderParam("Authorization") String token,
            @HeaderParam("X-Reference-Id") String referenceId,
            MTNPaymentRequest request);

    @GET
    @Path("/requesttopay/{referenceId}")
    MTNPaymentStatus getPaymentStatus(
            @HeaderParam("Authorization") String token,
            @PathParam("referenceId") String referenceId);
}
