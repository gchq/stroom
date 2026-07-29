/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.token;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.openid.api.TokenRevoker;
import stroom.security.shared.AppPermission;
import stroom.security.shared.TokenRevocationResource;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Objects;

/**
 * Revokes tokens minted by the internal IdP and propagates the revocation across the cluster.
 * <p>
 * Every operation is <b>a durable write first, then a best-effort fan-out</b>. Correctness rests entirely on
 * the {@code revoked} flag in the table; the fan-out only decides how quickly other nodes notice. A node that
 * misses the message converges when its denylist next reloads, so a lost call costs latency rather than
 * leaving a revoked token honoured indefinitely.
 * </p>
 * <p>
 * This is the OP half of a revocation only. Killing sessions and dropping held tokens is the RP's business and
 * is orchestrated on that side - see {@link TokenRevoker} for why the call only ever flows inward.
 * </p>
 */
@Singleton
public class TokenRevocationService implements TokenRevoker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TokenRevocationService.class);

    /**
     * Recorded as {@code revoked_by} when a grant is retired because the session holding it ended, so the audit
     * trail shows the cause rather than attributing it to whoever happened to be on the thread.
     */
    private static final String SESSION_ENDED_REVOKED_BY = "session-ended";

    private final OAuthTokenDao oAuthTokenDao;
    private final RevokedJtiCache revokedJtiCache;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final WebTargetFactory webTargetFactory;

    @Inject
    TokenRevocationService(final OAuthTokenDao oAuthTokenDao,
                           final RevokedJtiCache revokedJtiCache,
                           final SecurityContext securityContext,
                           final NodeInfo nodeInfo,
                           final NodeService nodeService,
                           final WebTargetFactory webTargetFactory) {
        this.oAuthTokenDao = oAuthTokenDao;
        this.revokedJtiCache = revokedJtiCache;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public int revokeForSubject(final String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return 0;
        }
        return secured(subjectId, () -> {
            final long now = System.currentTimeMillis();
            final int revoked = oAuthTokenDao.revokeBySubjectId(subjectId, revokedBy(), now);
            LOGGER.info(() -> LogUtil.message(
                    "Revoked {} token(s) for subject '{}'", revoked, subjectId));
            propagate();
            return revoked;
        });
    }

    @Override
    public int revokeGrant(final String jti) {
        if (jti == null || jti.isBlank()) {
            return 0;
        }
        // Deliberately not permission gated. This runs when a session ends, which includes idle expiry on a
        // container thread with no user context at all - a MANAGE_USERS check there would fail and leave the
        // grant live. It is safe ungated because it needs a jti, which identifies a specific grant, and the
        // only effect is retiring tokens whose holder has just gone away.
        final long now = System.currentTimeMillis();
        final int revoked = oAuthTokenDao.revokeGrantByJti(jti, SESSION_ENDED_REVOKED_BY, now);
        if (revoked > 0) {
            LOGGER.debug(() -> LogUtil.message(
                    "Revoked {} token(s) belonging to the grant of token '{}'", revoked, jti));
            propagate();
        }
        return revoked;
    }

    /**
     * Revoke one rotation lineage - every token descended from a single login, including the access and id
     * tokens it produced. Used for "sign this one device out" without touching the subject's other logins.
     */
    public int revokeFamily(final String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return 0;
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final long now = System.currentTimeMillis();
            final int revoked = oAuthTokenDao.revokeByFamilyId(familyId, revokedBy(), now);
            LOGGER.info(() -> LogUtil.message(
                    "Revoked {} token(s) in family '{}'", revoked, familyId));
            propagate();
            return revoked;
        });
    }

    /**
     * Revoke a single token. Available because the data supports it, but not a workflow an admin is expected
     * to drive - picking individual one-hour tokens out of a list is not a real task.
     */
    public boolean revokeJti(final String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            final long now = System.currentTimeMillis();
            final boolean revoked = oAuthTokenDao.revokeByJti(jti, revokedBy(), now);
            if (revoked) {
                LOGGER.info(() -> LogUtil.message("Revoked token with jti '{}'", jti));
                propagate();
            }
            return revoked;
        });
    }

    /**
     * A user may revoke their own tokens; revoking anyone else's requires MANAGE_USERS. Mirrors the split
     * already used for terminating sessions, so the two halves of a revocation are gated the same way.
     */
    private <T> T secured(final String subjectId, final java.util.function.Supplier<T> supplier) {
        final UserRef currentUser = securityContext.getUserRef();
        final boolean ownTokens = currentUser != null
                                  && Objects.equals(currentUser.getSubjectId(), subjectId);
        if (ownTokens) {
            return supplier.get();
        }
        return securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, supplier);
    }

    private String revokedBy() {
        final UserRef currentUser = securityContext.getUserRef();
        return currentUser != null
                ? currentUser.getSubjectId()
                : "system";
    }

    /**
     * Make the revocation take effect everywhere.
     * <p>
     * The local cache is invalidated directly; peers are told over REST. Each node then re-reads the denylist
     * from the table on next use, rather than the message carrying the revoked ids. That keeps the fan-out
     * payload-free and idempotent, and the extra read is negligible because revocation is rare - the objection
     * to piggybacking on {@code PermissionChangeEvent} was that *frequent* permission changes would each force
     * a re-read, which does not apply here.
     * </p>
     */
    private void propagate() {
        revokedJtiCache.invalidate();

        // Run the fan-out as the processing user. A user revoking their own tokens must still be able to reach
        // peer nodes, and the per-node endpoint requires MANAGE_USERS, which they may not hold.
        securityContext.asProcessingUser(() ->
                nodeService.findNodeNames(FindNodeCriteria.allEnabled())
                        .forEach(this::invalidateCacheOnNode));
    }

    private void invalidateCacheOnNode(final String nodeName) {
        try {
            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                // Already done above, before the fan-out started.
                return;
            }
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    TokenRevocationResource.BASE_PATH,
                    TokenRevocationResource.INVALIDATE_CACHE_PATH_PART);
            try {
                WebTarget webTarget = webTargetFactory.create(url);
                webTarget = UriBuilderUtil.addParam(
                        webTarget, TokenRevocationResource.NODE_NAME_PARAM, nodeName);
                try (final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        // The endpoint @Consumes JSON and takes no body, so send an empty JSON entity - a
                        // text/plain body would be rejected 415 by the remote node.
                        .post(Entity.json(""))) {
                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }
                }
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        } catch (final RuntimeException e) {
            // One unreachable node must not stop the others being told, and must not fail the revocation -
            // which has already been durably written. That node converges on its next cache reload.
            LOGGER.error(() -> LogUtil.message(
                    "Error invalidating the revoked token cache on node {}: {}. Enable DEBUG for stacktrace",
                    nodeName, e.getMessage()));
            LOGGER.debug(() -> LogUtil.message(
                    "Error invalidating the revoked token cache on node {}", nodeName), e);
        }
    }

    /**
     * Invalidate this node's denylist. The receiving end of the fan-out.
     */
    public void invalidateCacheOnThisNode() {
        securityContext.secureResult(AppPermission.MANAGE_USERS_PERMISSION, () -> {
            revokedJtiCache.invalidate();
            return true;
        });
    }
}
