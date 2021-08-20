package stroom.security.impl;

import stroom.docref.HasUuid;
import stroom.security.api.HasJws;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.TokenResponse;

import org.jose4j.jwt.JwtClaims;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpSession;

class UserIdentityImpl implements UserIdentity, HasSessionId, HasJws, HasUuid {

    private final String userUuid;
    private final String id;
    private final HttpSession httpSession;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile TokenResponse tokenResponse;
    private volatile JwtClaims jwtClaims;

    UserIdentityImpl(final String userUuid,
                     final String id,
                     final HttpSession httpSession,
                     final TokenResponse tokenResponse,
                     final JwtClaims jwtClaims) {
        this.userUuid = userUuid;
        this.id = id;
        this.httpSession = httpSession;

        this.tokenResponse = tokenResponse;
        this.jwtClaims = jwtClaims;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return userUuid;
    }

    @Override
    public String getJws() {
        return tokenResponse.getIdToken();
    }

    @Override
    public String getSessionId() {
        return httpSession.getId();
    }

    public void invalidateSession() {
        httpSession.invalidate();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public TokenResponse getTokenResponse() {
        return tokenResponse;
    }

    public void setTokenResponse(final TokenResponse tokenResponse) {
        this.tokenResponse = tokenResponse;
    }

    public JwtClaims getJwtClaims() {
        return jwtClaims;
    }

    public void setJwtClaims(final JwtClaims jwtClaims) {
        this.jwtClaims = jwtClaims;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserIdentityImpl that = (UserIdentityImpl) o;
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(id,
                that.id) && Objects.equals(httpSession.getId(), that.httpSession.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, id, httpSession.getId());
    }

    @Override
    public String toString() {
        return getId();
    }
}
