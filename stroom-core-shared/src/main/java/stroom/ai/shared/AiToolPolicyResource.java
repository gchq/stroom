/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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

import java.util.List;

/// Manages tool policies: which categories of Stroom's tools users and groups may use, in the "Ask Stroom AI" chat
/// and over MCP. Every call needs the Manage MCP Tool Policies permission.
@Tag(name = "AI Tool Policies")
@Path(AiToolPolicyResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AiToolPolicyResource extends RestResource, DirectRestService, FetchWithIntegerId<AiToolPolicy> {

    String BASE_PATH = "/aiToolPolicy" + ResourcePaths.V1;

    /// @return Every policy, by name.
    @GET
    @Path("/")
    @Operation(
            summary = "Lists the tool policies, by name",
            operationId = "listAiToolPolicies")
    List<AiToolPolicy> list();

    /// @param id A policy's id.
    /// @return The policy.
    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetches a tool policy by its id",
            operationId = "fetchAiToolPolicy")
    @Override
    AiToolPolicy fetch(@PathParam("id") Integer id);

    /// @param policy A new policy, with no id.
    /// @return The policy as saved, with its id.
    @POST
    @Path("/")
    @Operation(
            summary = "Creates a tool policy",
            operationId = "createAiToolPolicy")
    AiToolPolicy create(@Parameter(description = "policy", required = true) AiToolPolicy policy);

    /// @param id     The policy's id.
    /// @param policy The policy as changed, with the version it was read at.
    /// @return The policy as saved.
    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates a tool policy",
            operationId = "updateAiToolPolicy")
    AiToolPolicy update(@PathParam("id") int id,
                        @Parameter(description = "policy", required = true) AiToolPolicy policy);

    /// @param id A policy's id.
    /// @return True if it was deleted.
    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes a tool policy",
            operationId = "deleteAiToolPolicy")
    boolean delete(@PathParam("id") int id);

    /// @return Stroom's own tools, with their categories, for choosing what a policy grants.
    @GET
    @Path("/tools")
    @Operation(
            summary = "Lists Stroom's own tools, with their categories",
            operationId = "listAiTools")
    List<AiToolSummary> listTools();
}
