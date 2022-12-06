package stroom.security.common.impl;

import stroom.docref.HasUuid;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.TokenResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwt.JwtClaims;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpSession;

public class UserIdentityImpl
        extends AbstractTokenUserIdentity
        implements HasSessionId, HasUuid {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityImpl.class);

    private final String userUuid;
    private final HttpSession httpSession;

    public UserIdentityImpl(final String userUuid,
                            final String id,
                            final HttpSession httpSession,
                            final TokenResponse tokenResponse,
                            final JwtClaims jwtClaims) {
        super(id, tokenResponse, jwtClaims);
        this.userUuid = userUuid;
        this.httpSession = httpSession;
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
                "userUuid='" + userUuid + '\'' +
                ", id='" + getId() + '\'' +
                ", fullName='" + getFullName().orElse("") + '\'' +
                ", preferredUsername='" + getPreferredUsername() + '\'' +
                ", timeTilExpire='" + Duration.between(Instant.now(), getExpireTime()) + '\'' +
                '}';
    }
}
