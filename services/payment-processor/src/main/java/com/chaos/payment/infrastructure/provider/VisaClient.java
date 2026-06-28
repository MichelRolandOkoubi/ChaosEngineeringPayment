package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "visa-client")
@Path("/cybersource/payments/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface VisaClient {
    @POST
    @Path("/payments")
    VisaPaymentResponse processPayment(VisaPaymentRequest request);
}
