/*
 * Copyright 2020 Crown Copyright
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

package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;

public final class UserIdentitySessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentitySessionUtil.class);

    private static final String SESSION_USER_IDENTITY = "SESSION_USER_IDENTITY";
    // Who is using a session that an authenticating edge proxy (e.g. an AWS ALB) authenticates.
    // Informational only: it must never be used to authenticate a request.
    private static final String SESSION_EDGE_USER_REF = "SESSION_EDGE_USER_REF";

    private UserIdentitySessionUtil() {
    }

    /**
     * Set the {@link UserIdentity} on the session
     */
    public static void setUserInSession(final HttpSession session, final UserIdentity userIdentity) {
        Objects.requireNonNull(session);
        LOGGER.debug(() -> LogUtil.message("Setting userIdentity of type {} in session {}, userIdentity: {}",
                LogUtil.getSimpleClassName(userIdentity),
                NullSafe.get(session, HttpSession::getId),
                userIdentity));
        session.setAttribute(SESSION_USER_IDENTITY, userIdentity);
        if (userIdentity != null) {
            // The session is now owned by this identity, so any user recorded by an edge proxy
            // before it is out of date.
            session.removeAttribute(SESSION_EDGE_USER_REF);
        }
    }

    public static Optional<UserIdentity> getUserFromSession(final HttpSession session) {
        final Optional<UserIdentity> optUserIdentity = Optional.ofNullable(session)
                .map(session2 ->
                        (UserIdentity) session2.getAttribute(SESSION_USER_IDENTITY));

        if (LOGGER.isTraceEnabled()) {
            optUserIdentity.ifPresentOrElse(userIdentity -> {
                LOGGER.trace(() -> LogUtil.message("Got userIdentity of type {} in session {}, userIdentity: {}",
                        NullSafe.get(userIdentity, UserIdentity::getClass, Class::getSimpleName),
                        SessionUtil.getSessionId(session),
                        userIdentity));
            }, () ->
                    LOGGER.trace(() ->
                            LogUtil.message("No userIdentity in session {}", SessionUtil.getSessionId(session))));
        }
        return optUserIdentity;
    }

    /// Record the user of a session whose requests are authenticated by an authenticating edge
    /// proxy rather than by a [UserIdentity] held in the session.
    ///
    /// This exists only so the user can be shown in the session list. It is deliberately held
    /// apart from the identity set by [#setUserInSession(HttpSession, UserIdentity)], because
    /// the identity in the session authenticates later requests and this must not.
    ///
    /// A session should not hold both: setting an identity clears this, and this should not be
    /// set on a session that holds an identity.
    ///
    /// @param session The session to record the user against. Must not be null.
    /// @param userRef The user making requests on the session, or null to clear it.
    public static void setEdgeUserInSession(final HttpSession session, final UserRef userRef) {
        Objects.requireNonNull(session);
        LOGGER.trace(() -> LogUtil.message("Setting edge userRef {} in session {}",
                userRef,
                NullSafe.get(session, HttpSession::getId)));
        session.setAttribute(SESSION_EDGE_USER_REF, userRef);
    }

    /// Get the user recorded against a session by [#setEdgeUserInSession(HttpSession, UserRef)].
    ///
    /// For display only. Never use the result to authenticate a request.
    ///
    /// @param session The session to read, may be null.
    /// @return The recorded user, or empty if the session is null or has no recorded user.
    public static Optional<UserRef> getEdgeUserFromSession(final HttpSession session) {
        return Optional.ofNullable(SessionUtil.getAttribute(session, SESSION_EDGE_USER_REF, UserRef.class));
    }
}
