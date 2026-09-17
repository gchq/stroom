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

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.openid.api.TokenRevoker;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Cuts off a user's access, in one action, across both halves of the system.
 * <p>
 * The two halves exist because Stroom is simultaneously a relying party and (optionally) an identity provider,
 * and they stop different things:
 * </p>
 * <ul>
 *     <li><b>Token revocation</b> stops API access. A bearer token authenticates on its signature alone -
 *     {@code AbstractUserIdentityFactory.getApiUserIdentity} consults no session - so killing sessions does
 *     nothing to a stolen access token, which would otherwise keep working until it expired.</li>
 *     <li><b>Session eviction</b> stops UI access, and takes the refresh and access tokens held in those
 *     sessions with them. The denylist does not help here, because a session-authenticated request never
 *     presents a token to check.</li>
 * </ul>
 * <p>
 * Neither is sufficient alone, which is why this is a single operation rather than two things an administrator
 * has to remember to do. Callers that only want one half should call the underlying service directly and be
 * explicit about it.
 * </p>
 */
@Singleton
public class UserAccessRevocationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserAccessRevocationService.class);

    private final Provider<SessionListService> sessionListServiceProvider;
    private final Provider<TokenRevoker> tokenRevokerProvider;
    private final SecurityContext securityContext;

    @Inject
    UserAccessRevocationService(final Provider<SessionListService> sessionListServiceProvider,
                                final Provider<TokenRevoker> tokenRevokerProvider,
                                final SecurityContext securityContext) {
        this.sessionListServiceProvider = sessionListServiceProvider;
        this.tokenRevokerProvider = tokenRevokerProvider;
        this.securityContext = securityContext;
    }

    /**
     * Revoke every token issued to a subject by the internal IdP and terminate all of their sessions across
     * the cluster.
     * <p>
     * The account itself is untouched: it stays enabled and the password is unchanged. This forces
     * re-authentication, it does not lock anybody out permanently - locking is a separate, deliberate action.
     * </p>
     * <p>
     * Against an external IdP the token half is a natural no-op (nothing was minted here), so this reduces to
     * a cluster-wide sign-out. Callers surfacing this to a user should say so, because the external IdP will
     * happily issue fresh tokens on the next request.
     * </p>
     *
     * @param subjectId The subject whose access should be revoked.
     * @return the number of tokens revoked. Zero is normal and not a failure - it means the subject held no
     * unexpired internally-minted tokens.
     */
    public int revokeAccessForUser(final String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return 0;
        }
        final UserRef currentUser = securityContext.getUserRef();
        final boolean ownAccess = currentUser != null
                                  && Objects.equals(currentUser.getSubjectId(), subjectId);
        // A user may always cut off their own access; doing it to anyone else is an administrative act.
        if (ownAccess) {
            return doRevoke(subjectId);
        }
        return securityContext.secureResult(
                AppPermission.MANAGE_USERS_PERMISSION, () -> doRevoke(subjectId));
    }

    private int doRevoke(final String subjectId) {
        // Tokens first. A live session cannot obtain new tokens once its refresh family is dead, so doing this
        // half first shrinks the window in which the session being evicted could mint a replacement.
        final int tokensRevoked = tokenRevokerProvider.get().revokeForSubject(subjectId);

        // Then sessions, cluster-wide. Invalidating a session fires the container's destroy callback, which is
        // what removes the held tokens from the refresh queue - so the refresh manager does not resurrect it.
        sessionListServiceProvider.get().evictUserSessions(subjectId, null);

        LOGGER.info(() -> LogUtil.message(
                "Revoked access for subject '{}' - {} token(s) revoked and all sessions terminated",
                subjectId, tokensRevoked));
        return tokensRevoked;
    }
}
