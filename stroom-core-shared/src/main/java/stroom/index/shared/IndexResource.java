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

package stroom.index.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.FindWithCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Indexes (v2)")
@Path(IndexResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexResource extends RestResource, DirectRestService, FetchWithUuid<IndexDoc>,
        FindWithCriteria<FindIndexShardCriteria, IndexShard> {

    String BASE_PATH = "/index" + ResourcePaths.V2;
    String SHARD_DELETE_SUB_PATH = "/shard/delete";
    String SHARD_FLUSH_SUB_PATH = "/shard/flush";

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a index doc by its UUID",
            operationId = "fetchIndex")
    IndexDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an index doc",
            operationId = "updateIndex")
    IndexDoc update(@PathParam("uuid") String uuid,
                    @Parameter(description = "doc", required = true) IndexDoc doc);

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
    Long deleteIndexShards(@QueryParam("memberUuid") String memberUuid,
                           @Parameter(description = "criteria", required = true) FindIndexShardCriteria criteria);

    @POST
    @Path(SHARD_FLUSH_SUB_PATH)
    @Operation(
            summary = "Flush matching index shards",
            operationId = "flushIndexShards")
    Long flushIndexShards(@QueryParam("memberUuid") String memberUuid,
                          @Parameter(description = "criteria", required = true) FindIndexShardCriteria criteria);
}
