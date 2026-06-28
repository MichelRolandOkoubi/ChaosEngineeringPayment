package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "airtel-money-client")
@Path("/merchant/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AirtelMoneyClient {
    @POST
    @Path("/payments/")
    AirtelPaymentResponse requestPayment(AirtelPaymentRequest request);
}
