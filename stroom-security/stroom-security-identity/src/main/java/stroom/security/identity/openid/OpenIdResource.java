/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.openid;

import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenResponse;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.List;
import java.util.Map;

@Singleton
@Tag(name = OpenIdResource.AUTHENTICATION_TAG)
@Path("/oauth2/v1/noauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OpenIdResource extends RestResource {

    String API_KEYS_TAG = "API Keys";
    String AUTHENTICATION_TAG = "Authentication";

    @Operation(
            summary = "Submit an OpenId AuthenticationRequest.",
            operationId = "openIdAuth")
    @GET
    @Path("auth")
    void auth(
            @Context HttpServletRequest request,
            @QueryParam(OpenId.SCOPE) @NotNull String scope,
            @QueryParam(OpenId.RESPONSE_TYPE) @NotNull String responseType,
            @QueryParam(OpenId.CLIENT_ID) @NotNull String clientId,
            @QueryParam(OpenId.REDIRECT_URI) @NotNull String redirectUri,
            @QueryParam(OpenId.NONCE) @Nullable String nonce,
            @QueryParam(OpenId.STATE) @Nullable String state,
            @QueryParam(OpenId.PROMPT) @Nullable String prompt);

    @Operation(
            summary = "Get a token from an access code or refresh token",
            operationId = "openIdToken")
    @POST
    @Path("token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    TokenResponse token(MultivaluedMap<String, String> formParams);

    @Operation(
            summary = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            tags = API_KEYS_TAG,
            operationId = "openIdCerts")
    @GET
    @Path("certs")
    Map<String, List<Map<String, Object>>> certs(@Context @NotNull HttpServletRequest httpServletRequest);

    @Operation(
            summary = "Provides discovery for openid configuration",
            tags = API_KEYS_TAG,
            operationId = "openIdConfiguration")
    @GET
    @Path(".well-known/openid-configuration")
    String openIdConfiguration();
}
