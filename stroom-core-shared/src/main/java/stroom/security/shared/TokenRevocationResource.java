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

package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * The inter-node half of token revocation.
 * <p>
 * Revocation itself is a durable write, so this endpoint carries no authority of its own - it only tells a peer
 * node to drop its cached copy of the revoked-jti denylist so the write takes effect there immediately rather
 * than at the next natural reload. It is therefore idempotent, and losing the call costs latency, not
 * correctness.
 * </p>
 * <p>
 * Deliberately not a {@code DirectRestService}: nothing in the UI calls this, only other nodes.
 * </p>
 */
@Tag(name = "Token Revocation")
@Path(TokenRevocationResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TokenRevocationResource extends RestResource {

    String BASE_PATH = "/tokenRevocation" + ResourcePaths.V1;
    String INVALIDATE_CACHE_PATH_PART = "/invalidateCache";
    String NODE_NAME_PARAM = "nodeName";

    /**
     * Discard this node's cached revoked-jti denylist, so that it is re-read from the database on next use.
     *
     * @param nodeName The node being addressed, so the receiving node can tell a call meant for it from one it
     *                 should forward. Matches the convention used by the session endpoints.
     * @return true once the cache has been invalidated.
     */
    @POST
    @Path(INVALIDATE_CACHE_PATH_PART)
    @Operation(
            summary = "Tell a node to re-read the revoked token denylist (inter-node call)",
            operationId = "invalidateRevokedTokenCache")
    Boolean invalidateCache(@QueryParam(NODE_NAME_PARAM) String nodeName);
}
