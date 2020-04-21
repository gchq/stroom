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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "content - /v1")
@Path("/content" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ContentResource extends RestResource, DirectRestService {
    @POST
    @Path("import")
    @ApiOperation(
            value = "Import content",
            response = ResourceGeneration.class)
    ResourceKey importContent(ImportConfigRequest request);

    @POST
    @Path("confirmImport")
    @ApiOperation(
            value = "Get import confirmation state",
            response = List.class)
    List<ImportState> confirmImport(ResourceKey resourceKey);

    @POST
    @Path("export")
    @ApiOperation(
            value = "Export content",
            response = ResourceGeneration.class)
    ResourceGeneration exportContent(DocRefs docRefs);

    @POST
    @Path("fetchDependencies")
    @ApiOperation(
            value = "Fetch content dependencies",
            response = ResultPage.class)
    ResultPage<Dependency> fetchDependencies(DependencyCriteria criteria);
}