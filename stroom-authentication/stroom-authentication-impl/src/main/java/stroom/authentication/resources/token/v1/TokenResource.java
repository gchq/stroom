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
import event.logging.Event;
import event.logging.MultiObject;
import event.logging.ObjectOutcome;
import event.logging.Search;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jose4j.jwk.JsonWebKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.TokenVerifier;
import stroom.authentication.clients.AuthorisationService;
import stroom.authentication.config.StroomConfig;
import stroom.authentication.daos.TokenDao;
import stroom.authentication.daos.UserDao;
import stroom.authentication.resources.user.v1.User;
import stroom.authentication.service.eventlogging.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Singleton;
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
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Singleton
@Path("/token/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(description = "Stroom API Key API", tags = {"ApiKey"})
public class TokenResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenResource.class);

    private final TokenDao tokenDao;
    private final UserDao userDao;
    private AuthorisationService authorisationService;
    private TokenVerifier tokenVerifier;
    private StroomEventLoggingService stroomEventLoggingService;
    private StroomConfig stroomConfig;
    private SecurityContext securityContext;

    @Inject
    public TokenResource(final TokenDao tokenDao,
                         final UserDao userDao,
                         final AuthorisationService authorisationService,
                         final TokenVerifier tokenVerifier,
                         final StroomEventLoggingService stroomEventLoggingService,
                         final StroomConfig stroomConfig,
                         final SecurityContext securityContext) {
        this.tokenDao = tokenDao;
        this.userDao = userDao;
        this.authorisationService = authorisationService;
        this.tokenVerifier = tokenVerifier;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.stroomConfig = stroomConfig;
        this.securityContext = securityContext;
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
        Map<String, String> filters = searchRequest.getFilters();

        Search search = new Search();
        search.setType("token");
        // search.setQuery(); // TODO: More complete description of the search.
        stroomEventLoggingService.search(
                "SearchApiToken",
                httpServletRequest,
                securityContext.getUserId(),
                search,
                "The user searched for an API token.");
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            // Validate filters
            if (filters != null) {
                for (String key : filters.keySet()) {
                    switch (key) {
                        case "expiresOn":
                        case "issuedOn":
                        case "updatedOn":
                            return Response.status(Response.Status.BAD_REQUEST).entity("Filtering by date is not supported.").build();
                    }
                }
            }

            SearchResponse results = tokenDao.searchTokens(searchRequest);

            LOGGER.debug("Returning tokens: found " + results.getTokens().size());
            return Response.status(Response.Status.OK).entity(results).build();
        });
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

        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            final String userId = securityContext.getUserId();

            // Parse and validate tokenType
            Optional<Token.TokenType> tokenTypeToCreate = createTokenRequest.getParsedTokenType();
            if (!tokenTypeToCreate.isPresent()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Unknown token type:" + createTokenRequest.getTokenType()).build();
            }

            Instant expiryInstant = createTokenRequest.getExpiryDate() == null ? null : createTokenRequest.getExpiryDate().toInstant();
            Token token = tokenDao.createToken(
                    tokenTypeToCreate.get(),
                    userId,
                    expiryInstant,
                    createTokenRequest.getUserEmail(),
                    stroomConfig.getClientId(),
                    createTokenRequest.isEnabled(),
                    createTokenRequest.getComments());

            event.logging.Object object = new event.logging.Object();
            object.setId("NewToken");
            object.setName(token.getTokenType());
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.create(
                    "CreateApiToken",
                    httpServletRequest,
                    userId,
                    objectOutcome,
                    "Create a token");

            return Response.status(Response.Status.OK).entity(token).build();
        });
    }

    @ApiOperation(
            value = "Delete all tokens.",
            response = String.class,
            tags = {"ApiKey"})
    @DELETE
    @Timed
    public final Response deleteAll(
            @Context @NotNull HttpServletRequest httpServletRequest
    ) {

        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            tokenDao.deleteAllTokensExceptAdmins();

            event.logging.Object object = new event.logging.Object();
            object.setName("DeleteAllApiTokens");
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.delete(
                    "DeleteAllApiTokens",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Delete all tokens");

            return Response.status(Response.Status.OK).entity("All tokens deleted").build();
        });
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            tokenDao.deleteTokenById(tokenId);

            event.logging.Object object = new event.logging.Object();
            object.setId(Integer.valueOf(tokenId).toString());
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.delete(
                    "DeleteApiToken",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Delete a token by ID");

            return Response.status(Response.Status.NO_CONTENT).build();
        });
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {

            tokenDao.deleteTokenByTokenString(token);

            event.logging.Object object = new event.logging.Object();
            object.setId(token);
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.delete(
                    "DeleteApiToken",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Delete a token by the value of the actual token.");

            return Response.status(Response.Status.OK).entity("Deleted token").build();
        });
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            event.logging.Object object = new event.logging.Object();
            object.setId(token);
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.view(
                    "ReadApiToken",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Read a token by the string value of the token.");

            return tokenDao.readByToken(token)
                    .map(tokenResult -> Response.status(Response.Status.OK).entity(tokenResult).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            event.logging.Object object = new event.logging.Object();
            object.setId(Integer.valueOf(tokenId).toString());
            ObjectOutcome objectOutcome = new ObjectOutcome();
            objectOutcome.getObjects().add(object);
            stroomEventLoggingService.view(
                    "ReadApiToken",
                    httpServletRequest,
                    securityContext.getUserId(),
                    objectOutcome,
                    "Read a token by the token ID.");


            Optional<Token> optionalToken = tokenDao.readById(tokenId);
            return optionalToken
                    .map(token -> Response.status(Response.Status.OK).entity(token).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
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
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            final String userId = securityContext.getUserId();
            final event.logging.Object afterObject = new event.logging.Object();
            afterObject.setId(Integer.valueOf(tokenId).toString());
            MultiObject afterMultiObject = new MultiObject();
            afterMultiObject.getObjects().add(afterObject);

            Event.EventDetail.Update update = new Event.EventDetail.Update();
            update.setAfter(afterMultiObject);

            stroomEventLoggingService.update(
                    "ToggleApiTokenEnabled",
                    httpServletRequest,
                    userId,
                    update,
                    "Toggle whether a token is enabled or not.");

            Optional<User> updatingUser = userDao.get(userId);

            if (updatingUser.isPresent()) {
                tokenDao.enableOrDisableToken(tokenId, enabled, updatingUser.get());
                return Response.status(Response.Status.NO_CONTENT).build();
            } else {
                LOGGER.error("Unable to find the user that we just authenticated!");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        });
    }

    @ApiOperation(
            value = "Provides access to this service's current public key. " +
                    "A client may use these keys to verify JWTs issued by this service.",
            response = String.class,
            tags = {"ApiKey"})
    @GET
    @Path("/publickey")
    @Timed
    public final Response getPublicKey(
            @Context @NotNull HttpServletRequest httpServletRequest) {
        String jwkAsJson = tokenVerifier.getJwk().toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);

        event.logging.Object object = new event.logging.Object();
        object.setName("PublicKey");
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(object);
        stroomEventLoggingService.view(
                "GetPublicApiKey",
                httpServletRequest,
                "anonymous",
                objectOutcome,
                "Read a token by the token ID.");

        return Response
                .status(Response.Status.OK)
                .entity(jwkAsJson)
                .build();
    }
}
