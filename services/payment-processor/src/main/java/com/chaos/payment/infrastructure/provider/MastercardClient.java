package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "mastercard-client")
@Path("/api/rest/version/64")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MastercardClient {
    @POST
    @Path("/merchant/{merchantId}/session")
    MastercardPaymentResponse initiateSession(
            @PathParam("merchantId") String merchantId,
            MastercardPaymentRequest request);

    default MastercardPaymentResponse initiateSession(MastercardPaymentRequest request) {
        return initiateSession("default", request);
    }
}
