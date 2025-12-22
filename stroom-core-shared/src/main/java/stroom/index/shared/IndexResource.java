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

package stroom.index.shared;

import stroom.query.api.datasource.FindFieldCriteria;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.FindWithCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Indexes (v2)")
@Path(IndexResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexResource extends RestResource, DirectRestService, FetchWithUuid<LuceneIndexDoc>,
        FindWithCriteria<FindIndexShardCriteria, IndexShard> {

    String BASE_PATH = "/index" + ResourcePaths.V2;
    String SHARD_DELETE_SUB_PATH = "/shard/delete";
    String SHARD_FLUSH_SUB_PATH = "/shard/flush";
    String FIND_FIELDS_SUB_PATH = "/findFields";

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a index doc by its UUID",
            operationId = "fetchIndex")
    LuceneIndexDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an index doc",
            operationId = "updateIndex")
    LuceneIndexDoc update(@PathParam("uuid") String uuid,
                          @Parameter(description = "doc", required = true) LuceneIndexDoc doc);

    @POST
    @Path("/shard/find")
    @Operation(
            summary = "Find matching index shards",
            operationId = "findIndexShards")
    ResultPage<IndexShard> find(
            @Parameter(description = "criteria", required = true) FindIndexShardCriteria criteria);

    @POST
    @Path(SHARD_DELETE_SUB_PATH)
    @Operation(
            summary = "Delete matching index shards",
            operationId = "deleteIndexShards")
    Long deleteIndexShards(@QueryParam("nodeName") String nodeName,
                           @Parameter(description = "criteria", required = true) FindIndexShardCriteria criteria);

    @POST
    @Path(SHARD_FLUSH_SUB_PATH)
    @Operation(
            summary = "Flush matching index shards",
            operationId = "flushIndexShards")
    Long flushIndexShards(@QueryParam("nodeName") String nodeName,
                          @Parameter(description = "criteria", required = true) FindIndexShardCriteria criteria);

    @POST
    @Path(FIND_FIELDS_SUB_PATH)
    @Operation(
            summary = "Find index fields",
            operationId = "findFields")
    ResultPage<IndexFieldImpl> findFields(
            @Parameter(description = "criteria", required = true) FindFieldCriteria criteria);

    @POST
    @Path("/addField")
    @Operation(
            summary = "Add field",
            operationId = "addField")
    Boolean addField(
            @Parameter(description = "change", required = true) AddField addField);

    @PUT
    @Path("/updateField")
    @Operation(
            summary = "Update field",
            operationId = "updateField")
    Boolean updateField(
            @Parameter(description = "change", required = true) UpdateField updateField);

    @DELETE
    @Path("/deleteField")
    @Operation(
            summary = "Delete field",
            operationId = "deleteField")
    Boolean deleteField(
            @Parameter(description = "change", required = true) DeleteField deleteField);
}
