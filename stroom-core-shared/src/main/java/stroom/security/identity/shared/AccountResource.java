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

package stroom.security.identity.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Path(AccountResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Account")
public interface AccountResource extends RestResource, DirectRestService, FetchWithIntegerId<Account> {

    String BASE_PATH = "/account" + ResourcePaths.V1;

    FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField("UserId");
    FilterFieldDefinition FIELD_DEF_EMAIL = FilterFieldDefinition.qualifiedField("Email");
    FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField("Status");
    FilterFieldDefinition FIELD_DEF_FIRST_NAME = FilterFieldDefinition.qualifiedField("FirstName");
    FilterFieldDefinition FIELD_DEF_LAST_NAME = FilterFieldDefinition.qualifiedField("LastName");

    @Operation(
            summary = "Get all accounts.",
            operationId = "listAccounts")
    @GET
    @Path("/")
    @NotNull
    ResultPage<Account> list();

    @Operation(
            summary = "Search for an account by email.",
            operationId = "searchAccounts")
    @POST
    @Path("search")
    @NotNull
    ResultPage<Account> find(@Parameter(description = "account", required = true) FindAccountRequest request);

    @Operation(
            summary = "Create an account.",
            operationId = "createAccount")
    @POST
    @Path("/")
    @NotNull
    Integer create(@Parameter(description = "account", required = true) @NotNull CreateAccountRequest request);

    @Override
    @Operation(
            summary = "Get an account by ID.",
            operationId = "fetchAccount")
    @GET
    @Path("{id}")
    @NotNull
    Account fetch(@PathParam("id") final Integer accountId);

    @Operation(
            summary = "Update an account.",
            operationId = "updateAccount")
    @PUT
    @Path("{id}")
    @NotNull
    Boolean update(
            @Parameter(description = "account", required = true) @NotNull UpdateAccountRequest request,
            @PathParam("id") int accountId);

    @Operation(
            summary = "Delete an account by ID.",
            operationId = "deleteAccount")
    @DELETE
    @Path("{id}")
    @NotNull
    Boolean delete(@PathParam("id") int accountId);
}

