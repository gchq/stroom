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

import stroom.docref.DocRef;
import stroom.util.shared.ReadWithDocRef;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api(tags = "Indexes (v2)")
@Path(IndexResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexResource extends RestResource, DirectRestService, ReadWithDocRef<IndexDoc> {

    String BASE_PATH = "/index" + ResourcePaths.V2;
    String SHARD_DELETE_SUB_PATH = "/shard/delete";
    String SHARD_FLUSH_SUB_PATH = "/shard/flush";

    @Override
    @POST
    @Path("/read")
    @ApiOperation("Get an index doc")
    IndexDoc read(@ApiParam("docRef") DocRef docRef);

    @PUT
    @Path("/update")
    @ApiOperation("Update an index doc")
    IndexDoc update(IndexDoc indexDoc);

    @POST
    @Path("/shard/find")
    @ApiOperation("Find matching index shards")
    ResultPage<IndexShard> findIndexShards(@ApiParam("criteria") FindIndexShardCriteria criteria);

    @POST
    @Path(SHARD_DELETE_SUB_PATH)
    @ApiOperation("Delete matching index shards")
    Long deleteIndexShards(@QueryParam("nodeName") String nodeName,
                           @ApiParam("criteria") FindIndexShardCriteria criteria);

    @POST
    @Path(SHARD_FLUSH_SUB_PATH)
    @ApiOperation("Flush matching index shards")
    Long flushIndexShards(@QueryParam("nodeName") String nodeName,
                          @ApiParam("criteria") FindIndexShardCriteria criteria);
}
