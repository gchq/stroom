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

package stroom.index.shared;

import stroom.entity.shared.ExpressionCriteria;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Index Volumes")
@Path(IndexVolumeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource extends RestResource, DirectRestService, FetchWithIntegerId<IndexVolume> {

    String BASE_PATH = "/index/volume" + ResourcePaths.V2;
    String RESCAN_SUB_PATH = "/rescan";
    String VALIDATE_SUB_PATH = "/validate";

    @POST
    @Path("find")
    @Operation(
            summary = "Finds index volumes matching request",
            operationId = "findIndexVolumes")
    ResultPage<IndexVolume> find(@Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Path(VALIDATE_SUB_PATH)
    @Operation(
            summary = "Validates an index volume",
            operationId = "validateIndexVolume")
    ValidationResult validate(@Parameter(description = "request", required = true) IndexVolume request);

    @POST
    @Operation(
            summary = "Creates an index volume",
            operationId = "createIndexVolume")
    IndexVolume create(@Parameter(description = "request", required = true) IndexVolume request);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch an index volume",
            operationId = "fetchIndexVolume")
    IndexVolume fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates an index volume",
            operationId = "updateIndexVolume")
    IndexVolume update(@PathParam("id") Integer id,
                       @Parameter(description = "indexVolume", required = true) IndexVolume indexVolume);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes an index volume",
            operationId = "deleteIndexVolume")
    Boolean delete(@PathParam("id") Integer id);

    @GET
    @Path(RESCAN_SUB_PATH)
    @Operation(
            summary = "Rescans index volumes",
            operationId = "rescanIndexVolumes")
    Boolean rescan(@QueryParam("nodeName") String nodeName);

}
