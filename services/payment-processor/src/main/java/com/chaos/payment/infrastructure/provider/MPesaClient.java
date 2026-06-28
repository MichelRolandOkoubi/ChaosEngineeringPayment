package com.chaos.payment.infrastructure.provider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "mpesa-client")
@Path("/mpesa")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MPesaClient {
    @POST
    @Path("/stkpush/v1/processrequest")
    MPesaSTKPushResponse stkPush(MPesaSTKPushRequest request);
}
