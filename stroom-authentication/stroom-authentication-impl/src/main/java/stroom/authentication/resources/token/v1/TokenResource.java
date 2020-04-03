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

package stroom.authentication.resources.token.v1;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.authentication.JwkCache;
import stroom.authentication.dao.TokenDao;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@Path("/token/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(description = "Stroom API Key API", tags = {"ApiKey"})
public class TokenResource implements RestResource {

    private final TokenDao tokenDao;
    private TokenService service;

    @Inject
    public TokenResource(final TokenDao tokenDao,
                         final TokenService tokenService) {
        this.tokenDao = tokenDao;
        this.service = tokenService;
    }

    /**
     * Default ordering is by ISSUED_ON date, in descending order so the most recent tokens are shown first.
     * If orderBy is specified but orderDirection is not this will default to ascending.
     * <p>
     * The user must have the 'Manage Users' permission to call this.
     */
    @POST
    @Path("/search")
    @Timed
    @ApiOperation(
            value = "Submit a search request for tokens",
            response = SearchResponse.class,
            tags = {"ApiKey"})
    public final Response search(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("SearchRequest") @NotNull @Valid SearchRequest searchRequest) {
        var results = service.search(searchRequest);
        return Response.status(Response.Status.OK).entity(results).build();
    }

    @POST
    @Timed
    @ApiOperation(
            value = "Create a new token.",
            response = Token.class,
            tags = {"ApiKey"})
    public final Response create(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("CreateTokenRequest") @NotNull CreateTokenRequest createTokenRequest) {
        var token = service.create(createTokenRequest);
        return Response.status(Response.Status.OK).entity(token).build();
    }

    @ApiOperation(
            value = "Delete all tokens.",
            response = String.class,
            tags = {"ApiKey"})
    @DELETE
    @Timed
    public final Response deleteAll(@Context @NotNull HttpServletRequest httpServletRequest) {
        service.deleteAll();
        return Response.status(Response.Status.OK).entity("All tokens deleted").build();
    }

    @ApiOperation(
            value = "Delete a token by ID.",
            response = String.class,
            tags = {"ApiKey"})
    @DELETE
    @Path("/{id}")
    @Timed
    public final Response delete(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("id") int tokenId) {
        service.delete(tokenId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @ApiOperation(
            value = "Delete a token by the token string itself.",
            response = String.class,
            tags = {"ApiKey"})
    @DELETE
    @Path("/byToken/{token}")
    @Timed
    public final Response delete(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("token") String token) {
        service.delete(token);
        return Response.status(Response.Status.OK).entity("Deleted token").build();
    }

    @ApiOperation(
            value = "Read a token by the token string itself.",
            response = Token.class,
            tags = {"ApiKey"})
    @GET
    @Path("/byToken/{token}")
    @Timed
    public final Response read(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("token") String token) {
        return service.read(token)
                .map(tokenResult -> Response.status(Response.Status.OK).entity(tokenResult).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @ApiOperation(
            value = "Read a token by ID.",
            response = Token.class,
            tags = {"ApiKey"})
    @GET
    @Path("/{id}")
    @Timed
    public final Response read(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("id") int tokenId) {
        return tokenDao.readById(tokenId)
                .map(token -> Response.status(Response.Status.OK).entity(token).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @ApiOperation(
            value = "Enable or disable the state of a token.",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path("/{id}/state")
    @Timed
    public final Response toggleEnabled(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @NotNull @PathParam("id") int tokenId,
            @NotNull @QueryParam("enabled") boolean enabled) {
        service.toggleEnabled(tokenId, enabled);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @ApiOperation(
            value = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path("/publickey")
    @Timed
    public final Response getPublicKey(@Context @NotNull HttpServletRequest httpServletRequest) {
        String jwkAsJson = service.getPublicKey();
        return Response .status(Response.Status.OK) .entity(jwkAsJson) .build();
    }
}
