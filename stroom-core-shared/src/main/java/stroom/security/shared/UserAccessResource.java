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
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

/**
 * The administrative view of who currently holds access, and how to take it away.
 * <p>
 * Deliberately one screen covering both sessions and tokens. They stop different things - a bearer token
 * authenticates on its signature alone and ignores sessions entirely, while a session-authenticated request never
 * presents a token to check - so an interface that offered only one would let an administrator believe they had
 * cut someone off when they had not.
 * </p>
 */
@Tag(name = "User Access")
@Path(UserAccessResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserAccessResource extends RestResource, DirectRestService {

    String BASE_PATH = "/userAccess" + ResourcePaths.V1;
    String FIND_PATH_PART = "/find";
    String REVOKE_PATH_PART = "/revoke";
    String SESSIONS_PATH_PART = "/sessions";
    String SUBJECT_ID_PARAM = "subjectId";

    /**
     * List who holds live access, one row per subject, merging sessions with issued tokens.
     */
    @POST
    @Path(FIND_PATH_PART)
    @Operation(
            summary = "Find users holding live sessions or tokens",
            operationId = "findUserAccess")
    ResultPage<UserAccessRow> find(@NotNull FindUserAccessCriteria criteria);

    /**
     * The individual sessions one subject holds, so an administrator can end a single one rather than all of
     * them - signing one device out without disturbing the user's other logins.
     * <p>
     * Filtered server side rather than by the caller, so the client is never sent other users' session details.
     * </p>
     */
    @POST
    @Path(SESSIONS_PATH_PART)
    @Operation(
            summary = "List one user's sessions (requires Manage Users)",
            operationId = "findUserSessions")
    List<SessionDetails> listSessions(@QueryParam(SUBJECT_ID_PARAM) @NotNull String subjectId);

    /**
     * Revoke a subject's access: terminate every session they hold across the cluster and revoke every token the
     * internal IdP has issued them.
     * <p>
     * The account is left enabled and the password unchanged - this forces re-authentication rather than locking
     * anybody out. Against an external IdP only the session half has any effect, because nothing was minted here.
     * </p>
     *
     * @return the number of tokens revoked. Zero is normal, not a failure.
     */
    @POST
    @Path(REVOKE_PATH_PART)
    @Operation(
            summary = "Revoke a user's sessions and tokens (requires Manage Users)",
            operationId = "revokeUserAccess")
    Integer revokeAccess(@QueryParam(SUBJECT_ID_PARAM) @NotNull String subjectId);
}
