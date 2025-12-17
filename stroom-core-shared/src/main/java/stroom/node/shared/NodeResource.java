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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Nodes")
@Path(NodeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface NodeResource extends RestResource, DirectRestService {

    String BASE_PATH = "/node" + ResourcePaths.V1;
    String PING_PATH_PART = "/ping";
    String INFO_PATH_PART = "/info";
    String FIND_PATH_PART = "/find";
    String PRIORITY_PATH_PART = "/priority";
    String ENABLED_PATH_PART = "/enabled";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @GET
    @Path(INFO_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Gets detailed information about a node",
            operationId = "fetchNodeInfo")
    ClusterNodeInfo info(@PathParam("nodeName") String nodeName);

    @GET
    @Path("/all")
    @Operation(
            summary = "Lists all nodes",
            operationId = "listAllNodes")
    List<String> listAllNodes();

    @GET
    @Path("/enabled")
    @Operation(
            summary = "Lists enabled nodes",
            operationId = "listEnabledNodes")
    List<String> listEnabledNodes();

    @POST
    @Path(FIND_PATH_PART)
    @Operation(
            summary = "Finds nodes matching criteria and sort order",
            operationId = "findNodes")
    FetchNodeStatusResponse find(
            @Parameter(
                    description = "findNodeStatusCriteria",
                    required = true) final FindNodeStatusCriteria findNodeStatusCriteria);

    @GET
    @Path(PING_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Gets a ping time for a node",
            operationId = "pingNode")
    Long ping(@PathParam("nodeName") String nodeName);

    @PUT
    @Path(PRIORITY_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Sets the priority of a node",
            operationId = "setNodePriority")
    boolean setPriority(@PathParam("nodeName") String nodeName,
                        @Parameter(description = "nodeName", required = true) Integer priority);

    @PUT
    @Path(ENABLED_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Sets whether a node is enabled",
            operationId = "setNodeEnabled")
    boolean setEnabled(@PathParam("nodeName") String nodeName,
                       @Parameter(description = "enabled", required = true) Boolean enabled);
}
