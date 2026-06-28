package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wave-money-client")
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface WaveMoneyClient {
    @POST
    @Path("/checkout/sessions")
    WaveCheckoutResponse createCheckoutSession(WaveCheckoutRequest request);
}
