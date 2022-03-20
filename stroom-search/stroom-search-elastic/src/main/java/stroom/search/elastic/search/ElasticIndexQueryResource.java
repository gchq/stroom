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

package stroom.search.elastic.search;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceResource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Elasticsearch Queries")
@Path("/stroom-elastic-index" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ElasticIndexQueryResource extends DataSourceResource, RestResource {

    @POST
    @Path("/dataSource")
    @Operation(
            summary = "Submit a request for a data source definition, supplying the DocRef for the data source",
            operationId = "getElasticIndexDataSource")
    DataSource getDataSource(@Parameter(description = "DocRef", required = true) DocRef docRef);

    @POST
    @Path("/search")
    @Operation(
            summary = "Submit a search request",
            operationId = "searchElasticIndex")
    SearchResponse search(
            @Parameter(description = "SearchRequest", required = true) SearchRequest request);

    @POST
    @Path("/keepAlive")
    @Operation(
            summary = "Keep a running query alive",
            operationId = "keepAliveElasticIndexQuery")
    @Override
    Boolean keepAlive(@Parameter(description = "QueryKey", required = true) QueryKey queryKey);

    @POST
    @Path("/destroy")
    @Operation(
            summary = "Destroy a running query",
            operationId = "destroyElasticIndexQuery")
    Boolean destroy(@Parameter(description = "QueryKey", required = true) QueryKey queryKey);
}
