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

package stroom.security.identity.account;

import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;

@Path("/account/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(description = "Stroom Account API", tags = {"Account"})
public interface AccountResource extends RestResource {

    FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField("UserId");
    FilterFieldDefinition FIELD_DEF_EMAIL = FilterFieldDefinition.qualifiedField("Email");
    FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField("Status");
    FilterFieldDefinition FIELD_DEF_FIRST_NAME = FilterFieldDefinition.qualifiedField("FirstName");
    FilterFieldDefinition FIELD_DEF_LAST_NAME = FilterFieldDefinition.qualifiedField("LastName");

    List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_USER_ID,
            FIELD_DEF_EMAIL,
            FIELD_DEF_STATUS,
            FIELD_DEF_FIRST_NAME,
            FIELD_DEF_LAST_NAME);

    @ApiOperation(
            value = "Get all accounts.",
            response = String.class,
            tags = {"Account"})
    @GET
    @Path("/")
    @Timed
    @NotNull
    ResultPage<Account> list(@Context @NotNull HttpServletRequest httpServletRequest);

    @ApiOperation(
            value = "Search for an account by email.",
            response = String.class,
            tags = {"Account"})
    @POST
    @Path("search")
    @Timed
    @NotNull
    ResultPage<Account> search(SearchAccountRequest request);

    @ApiOperation(
            value = "Create an account.",
            response = Integer.class,
            tags = {"Account"})
    @POST
    @Path("/")
    @Timed
    @NotNull
    Integer create(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("account") @NotNull CreateAccountRequest request);

    @ApiOperation(
            value = "Get an account by ID.",
            response = String.class,
            tags = {"Account"})
    @GET
    @Path("{id}")
    @Timed
    @NotNull
    Account read(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("id") int accountId);

    @ApiOperation(
            value = "Update an account.",
            response = String.class,
            tags = {"Account"})
    @PUT
    @Path("{id}")
    @Timed
    @NotNull
    Boolean update(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @ApiParam("account") @NotNull UpdateAccountRequest request,
            @PathParam("id") int accountId);

    @ApiOperation(
            value = "Delete an account by ID.",
            response = String.class,
            tags = {"Account"})
    @DELETE
    @Path("{id}")
    @Timed
    @NotNull
    Boolean delete(
            @Context @NotNull HttpServletRequest httpServletRequest,
            @PathParam("id") int accountId);
}

