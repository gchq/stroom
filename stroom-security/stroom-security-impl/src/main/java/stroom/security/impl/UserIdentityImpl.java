package stroom.security.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSession;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.UpdatableToken;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.HasUserRef;
import stroom.util.NullSafe;
import stroom.util.authentication.HasRefreshable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;

public class UserIdentityImpl
        implements UserIdentity, HasSession, HasUserRef, HasJwt, HasRefreshable {

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
        this.userRef = new UserRef(
                Objects.requireNonNull(userUuid),
                Objects.requireNonNull(subjectId),
                displayName,
                fullName,
                false);
        this.httpSession = httpSession;
    }

    @Override
    public String getSubjectId() {
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
        return httpSession.getId();
    }

    public void invalidateSession() {
        httpSession.invalidate();
    }

    /**
     * Remove this {@link UserIdentity} from the HTTP session. This will require any future requests
     * to re-authenticate with the IDP.
     */
    public void removeUserFromSession() {
        UserIdentitySessionUtil.set(httpSession, null);
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
                optUserIdentity = UserIdentitySessionUtil.get(httpSession);
            } catch (Exception e) {
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
