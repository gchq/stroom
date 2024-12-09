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

package stroom.view.shared;

import stroom.docref.DocRef;
import stroom.util.shared.FetchWithUuid;
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

@Tag(name = "Views")
@Path("/view" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ViewResource extends RestResource, DirectRestService, FetchWithUuid<ViewDoc> {

    @POST
    @Path("/")
    @Operation(
            summary = "Create a view doc",
            operationId = "createView")
    ViewDoc create();

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a view doc by its UUID",
            operationId = "fetchView")
    ViewDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a view doc",
            operationId = "updateView")
    ViewDoc update(
            @PathParam("uuid") String uuid, @Parameter(description = "doc", required = true) ViewDoc doc);

    @GET
    @Path("/list")
    @Operation(
            summary = "Fetch view names",
            operationId = "listViews")
    List<DocRef> list();
}
