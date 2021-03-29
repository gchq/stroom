/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.token;

import stroom.security.identity.config.TokenConfig;
import stroom.util.shared.RestResource;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/token/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Api Keys")
public interface TokenResource extends RestResource {

    FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField("User Id");
    FilterFieldDefinition FIELD_DEF_USER_EMAIL = FilterFieldDefinition.qualifiedField("User Email");
    FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField("Status");
    FilterFieldDefinition FIELD_DEF_COMMENTS = FilterFieldDefinition.qualifiedField("Comments");

    @GET
    @Path("/")
    @NotNull
    @Operation(
            summary = "Get all tokens.",
            operationId = "listTokens")
    TokenResultPage list(@Context @NotNull HttpServletRequest httpServletRequest);

    @POST
    @Path("search")
    @Operation(
            summary = "Submit a search request for tokens",
            operationId = "searchTokens")
    TokenResultPage search(@Context @NotNull HttpServletRequest httpServletRequest,
                           @Parameter(description = "SearchRequest", required = true)
                           @NotNull
                           @Valid SearchTokenRequest request);

    @POST
    @Operation(
            summary = "Create a new token.",
            operationId = "createToken")
    Token create(@Context @NotNull HttpServletRequest httpServletRequest,
                 @Parameter(description = "CreateTokenRequest", required = true)
                 @NotNull CreateTokenRequest createTokenRequest);

    @Operation(
            summary = "Read a token by the token string itself.",
            operationId = "fetchTokenByContent")
    @GET
    @Path("/byToken/{token}")
    Token read(@Context @NotNull HttpServletRequest httpServletRequest,
               @PathParam("token") String token);

    @Operation(
            summary = "Read a token by ID.",
            operationId = "fetchToken")
    @GET
    @Path("/{id}")
    Token read(@Context @NotNull HttpServletRequest httpServletRequest,
               @PathParam("id") int tokenId);

    @Operation(
            summary = "Enable or disable the state of a token.",
            operationId = "toggleTokenEnabled")
    @GET
    @Path("/{id}/enabled")
    Integer toggleEnabled(@Context @NotNull HttpServletRequest httpServletRequest,
                          @NotNull @PathParam("id") int tokenId,
                          @NotNull @QueryParam("enabled") boolean enabled);

    @Operation(
            summary = "Delete a token by ID.",
            operationId = "deleteToken")
    @DELETE
    @Path("/{id}")
    Integer delete(@Context @NotNull HttpServletRequest httpServletRequest,
                   @PathParam("id") int tokenId);

    @Operation(
            summary = "Delete a token by the token string itself.",
            operationId = "deleteTokenByContent")
    @DELETE
    @Path("/byToken/{token}")
    Integer deleteByToken(@Context @NotNull HttpServletRequest httpServletRequest,
                          @PathParam("token") String token);

    @Operation(
            summary = "Delete all tokens.",
            operationId = "deleteAllTokens")
    @DELETE
    Integer deleteAll(@Context @NotNull HttpServletRequest httpServletRequest);


    @Operation(
            summary = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            operationId = "getPublicKey")
    @GET
    @Path("/publickey")
    String getPublicKey(@Context @NotNull HttpServletRequest httpServletRequest);

    @Operation(
            summary = "Get the token configuration",
            operationId = "fetchTokenConfig")
    @GET
    @Path("/noauth/fetchTokenConfig")
    @NotNull
    TokenConfig fetchTokenConfig();
}
