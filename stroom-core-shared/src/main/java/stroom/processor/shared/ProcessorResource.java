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

package stroom.processor.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Processors")
@Path("/processor" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProcessorResource extends RestResource, DirectRestService, FetchWithIntegerId<Processor> {

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch a processor",
            operationId = "fetchProcessor")
    Processor fetch(@PathParam("id") Integer id);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes a processor",
            operationId = "deleteProcessor")
    boolean delete(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}/enabled")
    @Operation(
            summary = "Sets the enabled/disabled state for a processor",
            operationId = "setProcessorEnabled")
    boolean setEnabled(@PathParam("id") Integer id,
                    @Parameter(description = "enabled", required = true) Boolean enabled);
}
