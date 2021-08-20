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

import stroom.util.shared.FetchWithIntegerId;
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

@Path("/apikey/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Api Keys")
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
