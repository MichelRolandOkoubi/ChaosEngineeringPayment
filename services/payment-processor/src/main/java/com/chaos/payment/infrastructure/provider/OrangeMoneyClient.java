package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "orange-money-client")
@Path("/orange-money-webpay/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OrangeMoneyClient {
    @POST
    @Path("/cashout/keys/{merchantKey}/simple")
    OrangeMoneyResponse initiatePayment(@PathParam("merchantKey") String merchantKey, OrangeMoneyRequest request);

    default OrangeMoneyResponse initiatePayment(OrangeMoneyRequest request) {
        return initiatePayment("default", request);
    }
}
