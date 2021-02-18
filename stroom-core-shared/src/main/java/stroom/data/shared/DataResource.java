/*
 * Copyright 2017 Crown Copyright
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

package stroom.data.shared;

import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Data")
@Path("/data" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DataResource extends RestResource, DirectRestService {

    @POST
    @Path("download")
    @Operation(summary = "Download matching data")
    ResourceGeneration download(@Parameter(description = "criteria", required = true) FindMetaCriteria criteria);

    @POST
    @Path("upload")
    @Operation(summary = "Upload data")
    ResourceKey upload(@Parameter(description = "request", required = true) UploadDataRequest request);

    @GET
    @Path("{id}/info")
    @Operation(summary = "Find full info about a data item")
    List<DataInfoSection> viewInfo(@PathParam("id") long id);

    @POST
    @Path("fetch")
    @Operation(summary = "Fetch matching data")
    AbstractFetchDataResult fetch(@Parameter(description = "request", required = true) FetchDataRequest request);

    @GET
    @Path("{id}/parts/{partNo}/child-types")
    @Operation(summary = "List child types for a stream")
    Set<String> getChildStreamTypes(@PathParam("id") final long id,
                                    @PathParam("partNo") final long partNo);
}
