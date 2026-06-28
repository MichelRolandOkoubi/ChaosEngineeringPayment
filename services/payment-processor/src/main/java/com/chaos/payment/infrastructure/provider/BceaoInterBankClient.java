package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "bceao-client")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface BceaoInterBankClient {
    @POST
    @Path("/virements")
    BceaoTransferResponse initiateTransfer(BceaoTransferRequest request);
}
