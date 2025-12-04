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

package stroom.cache.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Caches")
@Path(CacheResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CacheResource extends RestResource, DirectRestService {

    String BASE_PATH = "/cache" + ResourcePaths.V1;
    String LIST = "/list";
    String LIST_PATH = BASE_PATH + LIST;
    String INFO = "/info";
    String INFO_PATH = BASE_PATH + INFO;

    @GET
    @Path(LIST)
    @Operation(
            summary = "Lists caches",
            operationId = "listCaches")
    CacheNamesResponse list(@QueryParam("nodeName") String nodeName);

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
