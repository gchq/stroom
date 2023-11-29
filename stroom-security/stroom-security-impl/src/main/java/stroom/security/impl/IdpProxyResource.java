package stroom.security.impl;

import stroom.security.common.impl.ClientCredentials;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/idpproxy/v1/noauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "IdpProxy")
public interface IdpProxyResource extends RestResource {

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/fetchClientCredsToken")
    @Operation(
            summary = "Fetch an access token from the configured IDP using the supplied client credentials",
            operationId = "fetchClientCredsToken")
    String fetchToken(@Parameter(description = "clientCredentials", required = true
    ) final ClientCredentials clientCredentials);

}
