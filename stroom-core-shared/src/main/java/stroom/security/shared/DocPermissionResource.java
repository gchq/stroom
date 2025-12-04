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

package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Doc Permissions")
@Path("/permission/doc" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DocPermissionResource extends RestResource, DirectRestService {

    @POST
    @Path("/fetchDocumentUserPermissions")
    @Operation(
            summary = "Fetch document user permissions",
            operationId = "fetchDocumentUserPermissions")
    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            @Parameter(description = "request", required = true) FetchDocumentUserPermissionsRequest request);

    @POST
    @Path("/getDocUserPermissionsReport")
    @Operation(
            summary = "Get a detailed report of doc permissions for the specified user",
            operationId = "getDocUserPermissionsReport")
    DocumentUserPermissionsReport getDocUserPermissionsReport(@Parameter(description = "request", required = true)
                                                              DocumentUserPermissionsRequest request);

    /**
     * Check that the current user has the requested document permission.
     * This allows the UI to make some decisions but is not used for security purposes.
     *
     * @param request The request to find out if the current user has a certain document permission.
     * @return True if the permission is held.
     */
    @POST
    @Path("/checkDocumentPermission")
    @Operation(
            summary = "Check document permission",
            operationId = "checkDocumentPermission")
    Boolean checkDocumentPermission(
            @Parameter(description = "request", required = true) CheckDocumentPermissionRequest request);
}
