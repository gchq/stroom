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

package stroom.importexport.shared;

import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Content")
@Path("/content" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ContentResource extends RestResource, DirectRestService {

    @POST
    @Path("import")
    @Operation(
            summary = "Import content",
            operationId = "importContent")
    ImportConfigResponse importContent(
            @NotNull @Parameter(description = "request", required = true) ImportConfigRequest request);

    @POST
    @Path("export")
    @Operation(
            summary = "Export content",
            operationId = "exportContent")
    ResourceGeneration exportContent(
            @NotNull @Parameter(description = "docRefs", required = true) DocRefs docRefs);

    @POST
    @Path("fetchDependencies")
    @Operation(
            summary = "Fetch content dependencies",
            operationId = "fetchContentDependencies")
    ResultPage<Dependency> fetchDependencies(
            @Parameter(description = "criteria", required = true) DependencyCriteria criteria);
}
