/*
 * Copyright 2026 Crown Copyright
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

package stroom.quickfilter.shared;

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
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

/**
 * The filters the calling user has recently used in one quick filter, most recent first.
 * <p>
 * Everything here is scoped to the caller. There is no view of another user's history and no
 * administrative surface; the list is a convenience for the person who typed the filters, not
 * a record of what was searched - the searches the filters drive are what get audited.
 * <p>
 * All {@code POST} with a body: the key has two parts, and the codebase's
 * {@code DirectRestService} interfaces already prefer a request object to path segments for
 * anything beyond a single id.
 */
@Tag(name = "Quick Filter History")
@Path(QuickFilterHistoryResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface QuickFilterHistoryResource extends RestResource, DirectRestService {

    String BASE_PATH = "/quickFilterHistory" + ResourcePaths.V1;

    /**
     * Longer than this and it is not a quick filter. Silently not recorded rather than rejected,
     * because recording is fire-and-forget from the client's point of view.
     */
    int MAX_FILTER_TEXT_LENGTH = 400;

    @POST
    @Path("/fetch")
    @Operation(
            summary = "Fetch the calling user's recently used filters for one quick filter, most recent first",
            operationId = "fetchQuickFilterHistory")
    List<String> fetch(@Parameter(description = "key", required = true) QuickFilterHistoryKey key);

    @POST
    @Path("/record")
    @Operation(
            summary = "Record that the calling user used a filter in one quick filter",
            operationId = "recordQuickFilterUse")
    void record(@Parameter(description = "request", required = true) RecordQuickFilterUseRequest request);

    @POST
    @Path("/clear")
    @Operation(
            summary = "Forget the calling user's recently used filters for one quick filter",
            operationId = "clearQuickFilterHistory")
    void clear(@Parameter(description = "key", required = true) QuickFilterHistoryKey key);
}
