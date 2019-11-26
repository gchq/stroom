package stroom.security.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.model.IdTokenRequest;
import stroom.security.SecurityContext;

import org.springframework.stereotype.Component;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Api( value = "authentication - /v1")
@Path("/authentication/v1")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class AuthenticationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationResource.class);

    private final SecurityContext securityContext;
    private SecurityConfig config;
    private final AuthenticationServiceClients authenticationServiceClients;

    @Inject
    public AuthenticationResource(final SecurityContext securityContext,
                                  final AuthenticationServiceClients authenticationServiceClients,
                                  final SecurityConfig config) {
        this.securityContext = securityContext;
        this.authenticationServiceClients = authenticationServiceClients;
        this.config = config;
    }

    /**
     * Performs the back-channel exchange of accessCode for idToken.
     *
     * This must be kept as a back-channel request, and the clientSecret kept away from the browser.
     */
    @POST
    @Path("noauth/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Exchanges an accessCode for an idToken",
            response = Response.class)
    public Response exchangeAccessCode(@ApiParam("ExchangeAccessCodeRequest") final ExchangeAccessCodeRequest exchangeAccessCodeRequest) {
        IdTokenRequest idTokenRequest = new IdTokenRequest()
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .accessCode(exchangeAccessCodeRequest.getAccessCode());
        try {
            final String idToken = authenticationServiceClients.newAuthenticationApi().getIdToken(idTokenRequest);
            return Response.ok(idToken).build();
        } catch (ApiException e) {
            LOGGER.error("Unable to exchange the accessCode for an idToken", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
