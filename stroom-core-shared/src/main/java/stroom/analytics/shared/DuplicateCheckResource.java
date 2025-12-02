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

package stroom.analytics.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "DuplicateCheck")
@Path(DuplicateCheckResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DuplicateCheckResource extends RestResource, DirectRestService {

    String BASE_PATH = "/duplicateCheck" + ResourcePaths.V1;
    String FIND_SUB_PATH = "/find";
    String DELETE_SUB_PATH = "/delete";
    String FETCH_COL_NAME_SUB_PATH = "/fetchColumnNames";

    @POST
    @Path(FIND_SUB_PATH)
    @Operation(
            summary = "Find the duplicate check data for the current analytic",
            operationId = "findDuplicateCheckRows")
    DuplicateCheckRows find(@Parameter(description = "criteria", required = true)
                            FindDuplicateCheckCriteria criteria);

    @POST
    @Path(DELETE_SUB_PATH)
    @Operation(
            summary = "Delete duplicate check rows",
            operationId = "deleteDuplicateCheckRows")
    Boolean delete(@Parameter(description = "criteria", required = true)
                   DeleteDuplicateCheckRequest request);

    @POST
    @Path(FETCH_COL_NAME_SUB_PATH)
    @Operation(
            summary = "Fetch the column names from the dup check store for this analytic.",
            operationId = "fetchColumnNames")
    FetchColumnNamesResponse fetchColumnNames(String analyticUuid);
}
