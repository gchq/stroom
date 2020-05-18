package stroom.authentication.oauth2;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.authentication.api.OIDC;
import stroom.util.shared.RestResource;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Api
@Singleton
@Path("/oauth2/v1/noauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OAuth2Resource extends RestResource {

    @GET
    @Path("auth")
    @Timed
    @ApiOperation(value = "Submit an OpenId AuthenticationRequest.", response = String.class, tags = {"Authentication"})
    void auth(
            @Context HttpServletRequest request,
            @QueryParam(OIDC.SCOPE) @NotNull String scope,
            @QueryParam(OIDC.RESPONSE_TYPE) @NotNull String responseType,
            @QueryParam(OIDC.CLIENT_ID) @NotNull String clientId,
            @QueryParam(OIDC.REDIRECT_URI) @NotNull String redirectUri,
            @QueryParam(OIDC.NONCE) @Nullable String nonce,
            @QueryParam(OIDC.STATE) @Nullable String state,
            @QueryParam(OIDC.PROMPT) @Nullable String prompt);

    @POST
    @Path("token")
    @Timed
    @ApiOperation(value = "Get a token from an access code", response = String.class, tags = {"Authentication"})
    TokenResponse token(@ApiParam("tokenRequest") TokenRequest tokenRequest);

    @ApiOperation(
            value = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path("certs")
    @Timed
    Map<String, List<Map<String, Object>>> certs(@Context @NotNull HttpServletRequest httpServletRequest);
}
