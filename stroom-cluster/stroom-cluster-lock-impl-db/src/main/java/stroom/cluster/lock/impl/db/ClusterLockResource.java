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

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Cluster lock")
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
    @Operation(
            summary = "Try to lock",
            operationId = "tryClusterLock")
    Boolean tryLock(@PathParam("nodeName") String nodeName,
                    @Parameter(description = "key", required = true) ClusterLockKey key);

    @PUT
    @Path(RELEASE_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Release a lock",
            operationId = "releaseClusterLock")
    Boolean releaseLock(@PathParam("nodeName") String nodeName,
                        @Parameter(description = "key", required = true) ClusterLockKey key);

    @PUT
    @Path(KEEP_ALIVE_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Keep a lock alive",
            operationId = "keepClusterLockAlive")
    Boolean keepLockAlive(@PathParam("nodeName") String nodeName,
                          @Parameter(description = "key", required = true) ClusterLockKey key);
}
