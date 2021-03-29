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

package stroom.cache.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Caches")
@Path(CacheResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CacheResource extends RestResource, DirectRestService {

    String BASE_PATH = "/cache" + ResourcePaths.V1;
    String INFO = "/info";
    String INFO_PATH = BASE_PATH + INFO;

    @GET
    @Operation(
            summary = "Lists caches",
            operationId = "listCaches")
    List<String> list();

    @GET
    @Path(INFO)
    @Operation(
            summary = "Gets cache info",
            operationId = "getCacheInfo")
    CacheInfoResponse info(
            @QueryParam("cacheName") String cacheName,
            @QueryParam("nodeName") String nodeName);

    @DELETE
    @Operation(
            summary = "Clears a cache",
            operationId = "clearCache")
    Long clear(
            @QueryParam("cacheName") String cacheName,
            @QueryParam("nodeName") String nodeName);
}
