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

/**
 * Acts as a proxy for the Identity Provider. This is to allow callers with no details of the
 * identity provider, (other than the {@link ClientCredentials}) to make a token request on the
 * identity provider.
 * No authentication required as we are just proxying for unauthenticated endpoints on the IDP.
 */
@Path("/authproxy/v1/noauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "AuthProxy")
public interface AuthProxyResource extends RestResource {

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/fetchClientCredsToken")
    @Operation(
            summary = "Fetch an access token from the configured IDP using the supplied client credentials",
            operationId = "fetchClientCredsToken")
    String fetchToken(@Parameter(description = "clientCredentials", required = true
    ) final ClientCredentials clientCredentials);
}
