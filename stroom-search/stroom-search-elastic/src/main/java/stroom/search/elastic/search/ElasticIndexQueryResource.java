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

package stroom.search.elastic.search;

import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.datasource.DataSourceResource;
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

@Tag(name = "Elasticsearch Queries")
@Path("/stroom-elastic-index" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ElasticIndexQueryResource extends DataSourceResource, RestResource {

    @POST
    @Path("/search")
    @Operation(
            summary = "Submit a search request",
            operationId = "searchElasticIndex")
    SearchResponse search(
            @Parameter(description = "SearchRequest", required = true) SearchRequest request);

    @POST
    @Path("/destroy")
    @Operation(
            summary = "Destroy a running query",
            operationId = "destroyElasticIndexQuery")
    Boolean destroy(@Parameter(description = "QueryKey", required = true) QueryKey queryKey);
}
