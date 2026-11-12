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

package stroom.externaldatasource.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
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

/// REST resource for reading, saving and testing [ExternalDataSourceDoc] documents.
@Tag(name = "External Data Sources")
@Path("/externalDataSource" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExternalDataSourceResource
        extends RestResource, DirectRestService, FetchWithUuid<ExternalDataSourceDoc> {

    /// Fetches an external data source document.
    ///
    /// @param uuid The UUID of the document.
    /// @return The document.
    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch an external data source doc by its UUID",
            operationId = "fetchExternalDataSource")
    ExternalDataSourceDoc fetch(@PathParam("uuid") String uuid);

    /// Saves an external data source document.
    ///
    /// @param uuid The UUID of the document, which must match the document's own.
    /// @param doc  The document to save.
    /// @return The saved document.
    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an external data source doc",
            operationId = "updateExternalDataSource")
    ExternalDataSourceDoc update(
            @PathParam("uuid") String uuid,
            @Parameter(description = "doc", required = true) ExternalDataSourceDoc doc);

    /// Tests the connection described by a document, which need not be saved.
    ///
    /// The caller must be able to edit the document and use its credential.
    ///
    /// @param doc The document whose connection is tested.
    /// @return Whether the connection worked, and what was found.
    @POST
    @Path("/testConnection")
    @Operation(
            summary = "Test the connection of an external data source",
            operationId = "testExternalDataSourceConnection")
    ExternalDataSourceTestResponse testConnection(
            @Parameter(description = "externalDataSourceDoc", required = true) ExternalDataSourceDoc doc);

    /// Works out the columns of a document's CSV or Excel workbook, which need not be saved: their
    /// names from the header row and their types from the values. For an LDAP directory, works out the
    /// attributes of its first entries below the base DN, searching with the document's credential. A
    /// column of the same name already in the document keeps its type.
    ///
    /// The caller must be able to edit the document.
    ///
    /// @param doc The document, with its CSV settings and data, its Excel settings or its LDAP settings.
    /// @return The columns, in the order their values appear in each row, or the attributes in the order
    ///         they were first seen.
    @POST
    @Path("/detectColumns")
    @Operation(
            summary = "Detect the columns of an external data source's CSV or workbook, or the attributes of "
                      + "its LDAP directory",
            operationId = "detectExternalDataSourceColumns")
    List<DataColumn> detectColumns(
            @Parameter(description = "externalDataSourceDoc", required = true) ExternalDataSourceDoc doc);

    /// Prepares the workbook stored in an Excel document for download.
    ///
    /// The caller must be able to view the document.
    ///
    /// @param uuid The UUID of the document.
    /// @return The workbook, ready to download through the resource servlet.
    @GET
    @Path("/downloadExcelFile/{uuid}")
    @Operation(
            summary = "Download the workbook of an Excel external data source",
            operationId = "downloadExternalDataSourceExcelFile")
    ResourceGeneration downloadExcelFile(@PathParam("uuid") String uuid);
}
