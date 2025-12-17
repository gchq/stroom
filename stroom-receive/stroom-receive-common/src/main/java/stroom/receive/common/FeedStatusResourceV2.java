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

package stroom.receive.common;

import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
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

@Tag(name = "Feed Status")
@Path(FeedStatusResourceV2.BASE_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FeedStatusResourceV2 extends RestResource {

    String BASE_RESOURCE_PATH = "/feedStatus" + ResourcePaths.V2;
    String GET_FEED_STATUS_PATH_PART = "/getFeedStatus";

    @POST
    @Path(GET_FEED_STATUS_PATH_PART)
    @Operation(
            summary = "Submit a request to get the status of a feed",
            operationId = "getFeedStatus")
    GetFeedStatusResponse getFeedStatus(
            @Parameter(description = "GetFeedStatusRequest", required = true) GetFeedStatusRequestV2 request);
}
