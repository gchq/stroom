package stroom.security.impl;

import stroom.security.api.HasSession;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.HasUpdatableToken;
import stroom.security.common.impl.UpdatableToken;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwt.JwtClaims;

import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpSession;

public class UserIdentityImpl
        implements UserIdentity, HasSession, HasStroomUserIdentity, HasUpdatableToken {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityImpl.class);

    private final UpdatableToken updatableToken;
    private final String id;
    private final String userUuid;
    private final String displayName;
    private final String fullName;
    private final HttpSession httpSession;

    public UserIdentityImpl(final String userUuid,
                            final String id,
                            final HttpSession httpSession,
                            final UpdatableToken updatableToken) {
        this(userUuid, id, null, null, httpSession, updatableToken);
    }

    public UserIdentityImpl(final String userUuid,
                            final String id,
                            final String displayName,
                            final String fullName,
                            final HttpSession httpSession,
                            final UpdatableToken updatableToken) {
        this.id = Objects.requireNonNull(id);
        this.updatableToken = Objects.requireNonNull(updatableToken);
        this.userUuid = Objects.requireNonNull(userUuid);
        this.displayName = displayName;
        this.fullName = fullName;
        this.httpSession = httpSession;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return Objects.requireNonNullElse(displayName, id);
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    @Override
    public JwtClaims getJwtClaims() {
        return updatableToken.getJwtClaims();
    }

    @Override
    public String getUuid() {
        return userUuid;
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
    public UpdatableToken getUpdatableToken() {
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
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(httpSession, that.httpSession);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userUuid, httpSession);
    }

    @Override
    public String toString() {
        return "UserIdentityImpl{" +
                "updatableToken=" + updatableToken +
                ", id='" + id + '\'' +
                ", userUuid='" + userUuid + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fullName='" + fullName + '\'' +
                ", isInSession='" + isInSession() + '\'' +
                '}';
    }
}
