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

package stroom.security.identity.token;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.RestResource;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/apikey/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Api Keys")
@Deprecated // Keeping it else it breaks the React code
public interface ApiKeyResource extends RestResource, FetchWithIntegerId<ApiKey> {

    FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField("User Id");
    FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField("Status");

    @POST
    @Path("search")
    @Operation(
            summary = "Submit a search request for API keys",
            operationId = "searchApiKeys")
    ApiKeyResultPage search(@Context @NotNull HttpServletRequest httpServletRequest,
                            @Parameter(description = "SearchRequest", required = true)
                            @NotNull
                            @Valid SearchApiKeyRequest request);

    @POST
    @Operation(
            summary = "Create a new API key.",
            operationId = "createApiKey")
    ApiKey create(@Context @NotNull HttpServletRequest httpServletRequest,
                  @Parameter(description = "CreateApiKeyRequest", required = true)
                  @NotNull CreateApiKeyRequest createApiKeyRequest);

    @Operation(
            summary = "Read a API key by the data itself.",
            operationId = "fetchApiKeyByData")
    @GET
    @Path("/byData/{data}")
    ApiKey read(@Context @NotNull HttpServletRequest httpServletRequest,
                @PathParam("data") String data);

    @Operation(
            summary = "Read a API key by ID.",
            operationId = "fetchApiKey")
    @GET
    @Path("/{id}")
    ApiKey read(@Context @NotNull HttpServletRequest httpServletRequest,
                @PathParam("id") int id);

    @Operation(
            summary = "Enable or disable the state of an API key.",
            operationId = "toggleApiKeyEnabled")
    @GET
    @Path("/{id}/enabled")
    Integer toggleEnabled(@Context @NotNull HttpServletRequest httpServletRequest,
                          @NotNull @PathParam("id") int id,
                          @NotNull @QueryParam("enabled") boolean enabled);

    @Operation(
            summary = "Delete a API key by ID.",
            operationId = "deleteApiKey")
    @DELETE
    @Path("/{id}")
    Integer delete(@Context @NotNull HttpServletRequest httpServletRequest,
                   @PathParam("id") int id);

    @Operation(
            summary = "Delete an API key by the data itself.",
            operationId = "deleteApiKeyByData")
    @DELETE
    @Path("/byData/{data}")
    Integer delete(@Context @NotNull HttpServletRequest httpServletRequest,
                   @PathParam("data") String data);

    @Operation(
            summary = "Delete all API keys.",
            operationId = "deleteAllApiKeys")
    @DELETE
    Integer deleteAll(@Context @NotNull HttpServletRequest httpServletRequest);

    @Operation(
            summary = "Get the default time taken for API keys to expire",
            operationId = "getDefaultApiKeyExpirySeconds")
    @GET
    @Path("/noauth/getDefaultApiKeyExpirySeconds")
    Long getDefaultApiKeyExpirySeconds();
}
