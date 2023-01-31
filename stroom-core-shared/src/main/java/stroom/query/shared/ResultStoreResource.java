/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.shared;

import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.QueryKey;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "ResultStore")
@Path(ResultStoreResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ResultStoreResource extends RestResource, DirectRestService {

    String BASE_PATH = "/result-store" + ResourcePaths.V1;
    String LIST_PATH_PART = "/list";
    String FIND_PATH_PART = "/find";
    String UPDATE_PATH_PART = "/update";
    String TERMINATE_PATH_PART = "/terminate";
    String DESTROY_PATH_PART = "/destroy";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @GET
    @Path(LIST_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Lists result stores for a node",
            operationId = "listResultStores")
    ResultStoreResponse list(@PathParam("nodeName") String nodeName);

    @POST
    @Path(FIND_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Find the result stores matching the supplied criteria for a node",
            operationId = "findResultStores")
    ResultStoreResponse find(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "criteria", required = true) FindResultStoreCriteria criteria);

    @POST
    @Path(UPDATE_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Find the result stores matching the supplied criteria for a node",
            operationId = "updateResultStore")
    Boolean update(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) UpdateStoreRequest updateStoreRequest);

    @POST
    @Path(TERMINATE_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Terminates search tasks for a node",
            operationId = "terminateSearchTask")
    Boolean terminate(@PathParam("nodeName") String nodeName,
                      @Parameter(description = "queryKey", required = true) final QueryKey queryKey);

    @POST
    @Path(DESTROY_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Destroy a search result store for a node",
            operationId = "destroyResultStore")
    Boolean destroy(@PathParam("nodeName") String nodeName,
                    @Parameter(description = "request", required = true) final DestroyStoreRequest request);
}
