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

package stroom.data.store.impl.fs.shared;

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

@Tag(name = "Filesystem Volumes")
@Path(FsVolumeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FsVolumeResource extends RestResource, DirectRestService, FetchWithIntegerId<FsVolume> {

    String BASE_PATH = "/fsVolume" + ResourcePaths.V1;
    String VALIDATE_SUB_PATH = "/validate";

    @POST
    @Path("/find")
    @Operation(
            summary = "Finds volumes",
            operationId = "findFsVolumes")
    ResultPage<FsVolume> find(@Parameter(description = "criteria", required = true) FindFsVolumeCriteria criteria);

    @POST
    @Operation(
            summary = "Create a volume",
            operationId = "createFsVolume")
    FsVolume create(FsVolume volume);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Get a volume",
            operationId = "fetchFsVolume")
    FsVolume fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Update a volume",
            operationId = "updateFsVolume")
    FsVolume update(@PathParam("id") Integer id,
                    @Parameter(description = "volume", required = true) FsVolume volume);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Delete a volume",
            operationId = "deleteFsVolume")
    Boolean delete(@PathParam("id") Integer id);

    @GET
    @Path("/rescan")
    @Operation(
            summary = "Rescans volumes",
            operationId = "rescanFsVolumes")
    Boolean rescan();

    @POST
    @Path(VALIDATE_SUB_PATH)
    @Operation(
            summary = "Validates a volume",
            operationId = "validateFsVolume")
    ValidationResult validate(@Parameter(description = "request", required = true) FsVolume volume);
}
