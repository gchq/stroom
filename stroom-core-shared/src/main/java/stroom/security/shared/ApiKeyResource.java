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

package stroom.security.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.Collection;

@Tag(name = "API Key")
@Path(ApiKeyResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ApiKeyResource
        extends ApiKeyCheckResource, RestResource, DirectRestService, FetchWithIntegerId<HashedApiKey> {

    String BASE_PATH = "/apikey" + ResourcePaths.V2;

    @POST
    @Path("/")
    @Operation(
            summary = "Creates a new API key",
            operationId = "createApiKey")
    CreateHashedApiKeyResponse create(
            @Parameter(description = "request", required = true) final CreateHashedApiKeyRequest request);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch a dictionary doc by its UUID",
            operationId = "fetchApiKey")
    HashedApiKey fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Update a dictionary doc",
            operationId = "updateApiKey")
    HashedApiKey update(@PathParam("id") final int id,
                        @Parameter(description = "apiKey", required = true) final HashedApiKey apiKey);

    @Operation(
            summary = "Delete an API key by ID.",
            operationId = "deleteApiKey")
    @DELETE
    @Path("/{id}")
    boolean delete(@PathParam("id") final int id);

    @Operation(
            summary = "Delete a batch of API keys by ID.",
            operationId = "deleteApiKey")
    @DELETE
    @Path("/deleteBatch")
    int deleteBatch(@Parameter(description = "ids", required = true) final Collection<Integer> ids);

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the API keys matching the supplied criteria",
            operationId = "findApiKeysByCriteria")
    ResultPage<HashedApiKey> find(@Parameter(description = "criteria", required = true) FindApiKeyCriteria criteria);
}
