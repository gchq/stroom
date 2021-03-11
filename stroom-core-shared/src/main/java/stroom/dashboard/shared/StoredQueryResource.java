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

package stroom.dashboard.shared;

import stroom.util.shared.FetchWithTemplate;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Stored Queries")
@Path("/storedQuery" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface StoredQueryResource extends RestResource, DirectRestService, FetchWithTemplate<StoredQuery> {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find stored queries",
            operationId = "findStoredQueries")
    ResultPage<StoredQuery> find(
            @Parameter(description = "criteria", required = true) FindStoredQueryCriteria criteria);

    @POST
    @Path("/create")
    @Operation(
            summary = "Create a stored query",
            operationId = "createStoredQuery")
    StoredQuery create(@Parameter(description = "storedQuery", required = true) StoredQuery storedQuery);

    @POST
    @Path("/read")
    @Operation(
            summary = "Fetch a stored query",
            operationId = "fetchStoredQuery")
    @Override
    StoredQuery fetch(@Parameter(description = "storedQuery", required = true) StoredQuery storedQuery);

    @PUT
    @Path("/update")
    @Operation(
            summary = "Update a stored query",
            operationId = "updateStoredQuery")
    StoredQuery update(StoredQuery storedQuery);

    @DELETE
    @Path("/delete")
    @Operation(
            summary = "Delete a stored query",
            operationId = "deleteStoredQuery")
    Boolean delete(StoredQuery storedQuery);
}
