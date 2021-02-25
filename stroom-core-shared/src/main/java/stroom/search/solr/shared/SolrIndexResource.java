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

package stroom.search.solr.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Solr Indices")
@Path("/solrIndex" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SolrIndexResource extends RestResource, DirectRestService, FetchWithUuid<SolrIndexDoc> {

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a solr index doc by its UUID",
            operationId = "fetchSolrIndex")
    SolrIndexDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a solr index doc",
            operationId = "updateSolrIndex")
    SolrIndexDoc update(
            @PathParam("uuid") String uuid,
            @Parameter(description = "doc", required = true) SolrIndexDoc doc);

    @POST
    @Path("/fetchSolrTypes")
    @Operation(
            summary = "Fetch Solr types",
            operationId = "fetchSolrTypes")
    List<String> fetchSolrTypes(
            @Parameter(description = "solrIndexDoc", required = true) SolrIndexDoc solrIndexDoc);

    @POST
    @Path("/solrConnectionTest")
    @Operation(
            summary = "Test connection to Solr",
            operationId = "solrConnectionTest")
    SolrConnectionTestResponse solrConnectionTest(
            @Parameter(description = "solrIndexDoc", required = true) SolrIndexDoc solrIndexDoc);
}
