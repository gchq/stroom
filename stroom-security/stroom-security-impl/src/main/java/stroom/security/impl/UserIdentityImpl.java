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

package stroom.security.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSession;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.UpdatableToken;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.HasUserRef;
import stroom.util.authentication.HasExpiry;
import stroom.util.authentication.HasRefreshable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class UserIdentityImpl
        implements UserIdentity, HasSession, HasUserRef, HasJwt, HasRefreshable, HasExpiry {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityImpl.class);

    private final UpdatableToken updatableToken;
    private final UserRef userRef;
    private final HttpSession httpSession;

    public UserIdentityImpl(final String userUuid,
                            final String subjectId,
                            final HttpSession httpSession,
                            final UpdatableToken updatableToken) {
        this(userUuid, subjectId, null, null, httpSession, updatableToken);
    }

    public UserIdentityImpl(final String userUuid,
                            final String subjectId,
                            final String displayName,
                            final String fullName,
                            final HttpSession httpSession,
                            final UpdatableToken updatableToken) {
        this.updatableToken = Objects.requireNonNull(updatableToken);
        this.userRef = UserRef.builder()
                .uuid(Objects.requireNonNull(userUuid))
                .subjectId(Objects.requireNonNull(subjectId))
                .displayName(displayName)
                .fullName(fullName)
                .user()
                .enabled()
                .build();
        this.httpSession = httpSession;
    }

    @Override
    public String subjectId() {
        return userRef.getSubjectId();
    }

    @Override
    public String getDisplayName() {
        return userRef.toDisplayString();
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(userRef.getFullName());
    }

    @Override
    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String getSessionId() {
        return NullSafe.get(httpSession, HttpSession::getId);
    }

    public void invalidateSession() {
        NullSafe.consume(httpSession, HttpSession::invalidate);
    }

    /**
     * Remove this {@link UserIdentity} from the HTTP session. This will require any future requests
     * to re-authenticate with the IDP.
     */
    public void removeUserFromSession() {
        if (httpSession != null) {
            UserIdentitySessionUtil.setUserInSession(httpSession, null);
        }
    }

//    /**
//     * Allows for a small buffer before the actual expiry time.
//     * Either means we need to use the refresh token to get new tokens or if there is no
//     * refresh token then we need to request new tokens without using a refresh token.
//     */
//    boolean isTokenRefreshRequired() {
//        final boolean inSession = isInSession();
//        final boolean isTokenRefreshRequired = super.isTokenRefreshRequired();
//        LOGGER.trace("isTokenRefreshRequired called, super.isTokenRefreshRequired:{} , isInSession: {}",
//                isTokenRefreshRequired, inSession);
//        return isTokenRefreshRequired && inSession;
//    }

    /**
     * @return True if this {@link UserIdentity} has a session and is an attribute value in that session
     */
    public boolean isInSession() {
        if (httpSession == null) {
            return false;
        } else {
            final Optional<UserIdentity> optUserIdentity;

            try {
                optUserIdentity = UserIdentitySessionUtil.getUserFromSession(httpSession);
            } catch (final Exception e) {
                LOGGER.debug(() -> LogUtil.message(
                        "Error getting identity from session, likely due to it being removed at logout: {}",
                        e.getMessage()));
                return false;
            }

            if (optUserIdentity.isPresent()) {
                final UserIdentity sessionUserIdentity = optUserIdentity.get();

                if (sessionUserIdentity == this) {
                    return true;
                } else {
                    LOGGER.debug("UserIdentity in session is different instance, {} vs {}",
                            sessionUserIdentity, this);
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public UpdatableToken getRefreshable() {
        return updatableToken;
    }

    @Override
    public Instant getExpireTime() {
        return updatableToken.getExpireTime();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final UserIdentityImpl that = (UserIdentityImpl) o;
        return Objects.equals(userRef, that.userRef) && Objects.equals(httpSession, that.httpSession);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userRef, httpSession);
    }

    @Override
    public String toString() {
        return "UserIdentityImpl{" +
               "updatableToken=" + updatableToken +
               ", userRef='" + userRef + '\'' +
               ", isInSession='" + isInSession() + '\'' +
               '}';
    }

    @Override
    public String getJwt() {
        return NullSafe.get(updatableToken, UpdatableToken::getJwt);
    }
}
