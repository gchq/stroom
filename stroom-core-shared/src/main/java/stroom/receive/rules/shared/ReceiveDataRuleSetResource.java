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

package stroom.receive.rules.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Rule Set")
@Path(ReceiveDataRuleSetResource.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReceiveDataRuleSetResource extends RestResource, DirectRestService {

    String BASE_RESOURCE_PATH = "/ruleset" + ResourcePaths.V2;
    String FETCH_HASHED_PATH_PART = "/fetchHashedRules";

    @GET
    @Path("/")
    @Operation(
            summary = "Fetch receipt rules",
            operationId = "fetchReceiveDataRules")
    ReceiveDataRules fetch();


    @SuppressWarnings("unused") // Called by proxy
    @GET
    @Path(FETCH_HASHED_PATH_PART)
    @Operation(
            summary = "Fetch hashed receipt rules",
            operationId = "fetchHashedReceiveDataRules")
    HashedReceiveDataRules fetchHashedRules();

    @PUT
    @Path("/")
    @Operation(
            summary = "Update receipt rules",
            operationId = "updateReceiveDataRules")
    ReceiveDataRules update(@Parameter(description = "doc", required = true) ReceiveDataRules doc);
}
