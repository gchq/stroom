/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqldatasource.shared;

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

/// REST resource for reading, saving and testing [SqlDataSourceDoc] documents.
@Tag(name = "SQL Data Sources")
@Path("/sqlDataSource" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SqlDataSourceResource extends RestResource, DirectRestService, FetchWithUuid<SqlDataSourceDoc> {

    /// Fetches a SQL data source document.
    ///
    /// @param uuid The UUID of the document.
    /// @return The document.
    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a SQL data source doc by its UUID",
            operationId = "fetchSqlDataSource")
    SqlDataSourceDoc fetch(@PathParam("uuid") String uuid);

    /// Saves a SQL data source document.
    ///
    /// @param uuid The UUID of the document, which must match the document's own.
    /// @param doc  The document to save.
    /// @return The saved document.
    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a SQL data source doc",
            operationId = "updateSqlDataSource")
    SqlDataSourceDoc update(
            @PathParam("uuid") String uuid,
            @Parameter(description = "doc", required = true) SqlDataSourceDoc doc);

    /// Tests the connection described by a document, which need not be saved.
    ///
    /// The caller must be able to edit the document and use its credential.
    ///
    /// @param doc The document whose connection is tested.
    /// @return Whether the connection worked, and what was found.
    @POST
    @Path("/testConnection")
    @Operation(
            summary = "Test the connection of a SQL data source",
            operationId = "testSqlDataSourceConnection")
    SqlDataSourceTestResponse testConnection(
            @Parameter(description = "sqlDataSourceDoc", required = true) SqlDataSourceDoc doc);
}
