package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "bitcoin-client")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface BitcoinClient {
    @POST
    @Path("/invoices")
    BTCPaymentResponse createInvoice(BTCPaymentRequest request);
}
