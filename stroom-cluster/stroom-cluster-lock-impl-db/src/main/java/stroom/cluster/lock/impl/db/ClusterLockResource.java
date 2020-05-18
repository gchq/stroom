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

package stroom.cluster.lock.impl.db;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "cluster/lock - /v1")
@Path(ClusterLockResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ClusterLockResource extends RestResource, DirectRestService {
    String BASE_PATH = "/cluster/lock" + ResourcePaths.V1;
    String TRY_PATH_PART = "/try";
    String RELEASE_PATH_PART = "/release";
    String KEEP_ALIVE_PATH_PART = "/keepALive";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @PUT
    @Path(TRY_PATH_PART + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Try to lock")
    Boolean tryLock(@PathParam("nodeName") String nodeName, 
                    @ApiParam("key") ClusterLockKey key);

    @PUT
    @Path(RELEASE_PATH_PART + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Release a lock")
    Boolean releaseLock(@PathParam("nodeName") String nodeName, 
                        @ApiParam("key") ClusterLockKey key);

    @PUT
    @Path(KEEP_ALIVE_PATH_PART + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Keep a lock alive")
    Boolean keepLockAlive(@PathParam("nodeName") String nodeName, 
                          @ApiParam("key") ClusterLockKey key);
}
