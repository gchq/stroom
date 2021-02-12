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

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

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
@Api(tags = {"ApiKey"})
public interface TokenResource extends RestResource {

    FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField("User Id");
    FilterFieldDefinition FIELD_DEF_USER_EMAIL = FilterFieldDefinition.qualifiedField("User Email");
    FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField("Status");
    FilterFieldDefinition FIELD_DEF_COMMENTS = FilterFieldDefinition.qualifiedField("Comments");

    @GET
    @Path("/")
    @Timed
    @NotNull
    @ApiOperation(value = "Get all tokens.")
    TokenResultPage list(@Context @NotNull HttpServletRequest httpServletRequest);

    @POST
    @Path("search")
    @Timed
    @ApiOperation(value = "Submit a search request for tokens")
    TokenResultPage search(@Context @NotNull HttpServletRequest httpServletRequest,
                           @ApiParam("SearchRequest") @NotNull @Valid SearchTokenRequest request);

    @POST
    @Timed
    @ApiOperation(value = "Create a new token.")
    Token create(@Context @NotNull HttpServletRequest httpServletRequest,
                 @ApiParam("CreateTokenRequest") @NotNull CreateTokenRequest createTokenRequest);

    @ApiOperation(value = "Read a token by the token string itself.")
    @GET
    @Path("/byToken/{token}")
    @Timed
    Token read(@Context @NotNull HttpServletRequest httpServletRequest,
               @PathParam("token") String token);

    @ApiOperation(value = "Read a token by ID.")
    @GET
    @Path("/{id}")
    @Timed
    Token read(@Context @NotNull HttpServletRequest httpServletRequest,
               @PathParam("id") int tokenId);

    @ApiOperation(value = "Enable or disable the state of a token.")
    @GET
    @Path("/{id}/enabled")
    @Timed
    Integer toggleEnabled(@Context @NotNull HttpServletRequest httpServletRequest,
                          @NotNull @PathParam("id") int tokenId,
                          @NotNull @QueryParam("enabled") boolean enabled);

    @ApiOperation(value = "Delete a token by ID.")
    @DELETE
    @Path("/{id}")
    @Timed
    Integer delete(@Context @NotNull HttpServletRequest httpServletRequest,
                   @PathParam("id") int tokenId);

    @ApiOperation(value = "Delete a token by the token string itself.")
    @DELETE
    @Path("/byToken/{token}")
    @Timed
    Integer deleteByToken(@Context @NotNull HttpServletRequest httpServletRequest,
                          @PathParam("token") String token);

    @ApiOperation(value = "Delete all tokens.")
    @DELETE
    @Timed
    Integer deleteAll(@Context @NotNull HttpServletRequest httpServletRequest);


    @ApiOperation(value = "Provides access to this service's current public key. " +
            "A client may use these keys to verify JWTs issued by this service.")
    @GET
    @Path("/publickey")
    @Timed
    String getPublicKey(@Context @NotNull HttpServletRequest httpServletRequest);

    @GET
    @Path("/noauth/fetchTokenConfig")
    @Timed
    @NotNull
    @ApiOperation(value = "Get the token configuration")
    TokenConfig fetchTokenConfig();
}
