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

package stroom.data.store.api;

import stroom.meta.shared.FindMetaCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Tag(name = "Data Download")
@Path("/dataDownload" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DataDownloadResource extends RestResource {

    /*
    This method is defined separately to `DataResource` due to GWT incompatibility with the jaxax `Response` class.
     */
    @POST
    @Path("downloadZip")
    @Operation(
            summary = "Retrieve content matching the provided criteria as a zip file",
            operationId = "downloadZip",
            responses = {
                    @ApiResponse(description = "Returns Stroom content data as a zip file")
            })
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadZip(@Parameter(description = "criteria", required = true) FindMetaCriteria criteria);
}
