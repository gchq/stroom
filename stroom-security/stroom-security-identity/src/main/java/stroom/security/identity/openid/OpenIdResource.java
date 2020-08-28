package stroom.security.identity.openid;

import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenResponse;
import stroom.util.shared.RestResource;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

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
public interface OpenIdResource extends RestResource {
    @GET
    @Path("auth")
    @Timed
    @ApiOperation(value = "Submit an OpenId AuthenticationRequest.", response = String.class, tags = {"Authentication"})
    void auth(
            @Context HttpServletRequest request,
            @QueryParam(OpenId.SCOPE) @NotNull String scope,
            @QueryParam(OpenId.RESPONSE_TYPE) @NotNull String responseType,
            @QueryParam(OpenId.CLIENT_ID) @NotNull String clientId,
            @QueryParam(OpenId.REDIRECT_URI) @NotNull String redirectUri,
            @QueryParam(OpenId.NONCE) @Nullable String nonce,
            @QueryParam(OpenId.STATE) @Nullable String state,
            @QueryParam(OpenId.PROMPT) @Nullable String prompt);

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

    @ApiOperation(
            value = "Provides discovery for openid configuration",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path(".well-known/openid-configuration")
    @Timed
    String openIdConfiguration();
}
