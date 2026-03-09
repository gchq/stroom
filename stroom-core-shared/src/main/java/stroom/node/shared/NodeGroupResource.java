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

package stroom.node.shared;

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

@Tag(name = "Node Groups")
@Path("/node/nodeGroup" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface NodeGroupResource extends RestResource, DirectRestService {

    @POST
    @Path("find")
    @Operation(
            summary = "Finds node groups matching request",
            operationId = "findNodeGroups")
    ResultPage<NodeGroup> find(
            @Parameter(description = "request", required = true) FindNodeGroupRequest request);

    @POST
    @Operation(
            summary = "Creates a node group",
            operationId = "createNodeGroup")
    NodeGroup create(@Parameter(description = "name", required = true) String name);

    @GET
    @Path("/fetchById/{id}")
    @Operation(
            summary = "Gets an node group by id",
            operationId = "fetchNodeGroupById")
    NodeGroup fetchById(@PathParam("id") Integer id);

    @GET
    @Path("/fetchByName/{name}")
    @Operation(
            summary = "Gets an node group by name",
            operationId = "fetchNodeGroupByName")
    NodeGroup fetchByName(@PathParam("name") String name);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates an node group",
            operationId = "updateNodeGroup")
    NodeGroup update(@PathParam("id") Integer id, NodeGroup nodeGroup);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes an node group",
            operationId = "deleteNodeGroup")
    Boolean delete(@PathParam("id") Integer id);

    @GET
    @Path("/getNodeGroupStates/{id}")
    @Operation(
            summary = "Gets node group states",
            operationId = "getNodeGroupState")
    ResultPage<NodeGroupState> getNodeGroupState(@PathParam("id") Integer id);

    @POST
    @Path("/updateNodeGroupState")
    @Operation(
            summary = "Updates node group state",
            operationId = "updateNodeGroupState")
    Boolean updateNodeGroupState(
            @Parameter(description = "nodeGroupState", required = true) NodeGroupChange change);
}
