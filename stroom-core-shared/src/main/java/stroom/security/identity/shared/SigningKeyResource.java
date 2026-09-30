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

package stroom.security.identity.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

/**
 * The internal identity provider's signing keys, and the ability to withdraw one.
 * <p>
 * Every operation requires the Administrator permission. Revoking a signing key signs out everyone using the
 * internal identity provider, including the processing user that nodes authenticate to each other with, so
 * it is an application-wide act rather than a user-management one.
 * </p>
 */
@Tag(name = "Signing Keys")
@Path(SigningKeyResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SigningKeyResource extends RestResource, DirectRestService {

    String BASE_PATH = "/signingKey" + ResourcePaths.V1;
    String LIST_PATH_PART = "/list";
    String REVOKE_PATH_PART = "/revoke";
    String REVOKE_ALL_PATH_PART = "/revokeAll";
    String REFRESH_PATH_PART = "/refresh";
    String ID_PARAM = "id";
    String NODE_NAME_PARAM = "nodeName";

    /**
     * Every signing key, newest first, including those already revoked or expired - unlike the published key
     * set, which by definition contains only the ones still trusted.
     */
    @GET
    @Path(LIST_PATH_PART)
    @Operation(
            summary = "List the internal identity provider's signing keys (requires Administrator)",
            operationId = "listSigningKeys")
    List<SigningKeyRow> list();

    /**
     * Withdraw one key. Anything signed with it stops being accepted at once, and a replacement is created
     * in the same breath if the revoked key was the one signing new tokens.
     *
     * @return the number of keys revoked, which is zero if it was already revoked.
     */
    @POST
    @Path(REVOKE_PATH_PART)
    @Operation(
            summary = "Revoke one signing key (requires Administrator)",
            operationId = "revokeSigningKey")
    Integer revoke(@QueryParam(ID_PARAM) @NotNull Integer id);

    /**
     * Withdraw every key that is still trusted, and create a replacement. For a compromise where it is not
     * known which key was exposed, which is the usual case.
     *
     * @return the number of keys revoked.
     */
    @POST
    @Path(REVOKE_ALL_PATH_PART)
    @Operation(
            summary = "Revoke all signing keys (requires Administrator)",
            operationId = "revokeAllSigningKeys")
    Integer revokeAll();

    /**
     * Make this node forget its cached copy of the key set.
     * <p>
     * Called on every node after a revocation. Each node caches the keys, so without this a withdrawn key
     * would go on being accepted everywhere except the node that handled the revocation until that cache
     * happened to reload on its own.
     * </p>
     */
    @POST
    @Path(REFRESH_PATH_PART)
    @Operation(
            summary = "Discard this node's cached signing keys (requires Administrator)",
            operationId = "refreshSigningKeys")
    Boolean refreshOnNode(@QueryParam(NODE_NAME_PARAM) @NotNull String nodeName);
}
