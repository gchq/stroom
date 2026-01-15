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

package stroom.explorer.shared;

import stroom.security.shared.SingleDocumentPermissionChangeRequest;
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

/**
 * Resource to provide a central place for non-Explorer stuff to change permissions.
 */
@Tag(name = "PermissionChange")
@Path("/permission")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PermissionChangeResource extends RestResource, DirectRestService {

    /**
     * Change the permission of something that isn't in the Explorer Tree.
     */
    @POST
    @Path("/changeDocumentPermissions")
    @Operation(
            summary = "Change document permissions",
            operationId = "changeDocumentPermissions")
    Boolean changeDocumentPermissions(
            @Parameter(description = "request", required = true) SingleDocumentPermissionChangeRequest request);

}
