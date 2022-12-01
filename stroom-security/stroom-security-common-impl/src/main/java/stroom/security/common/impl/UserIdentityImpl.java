package stroom.security.common.impl;

import stroom.docref.HasUuid;
import stroom.security.api.HasJwt;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenResponse;

import org.jose4j.jwt.JwtClaims;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpSession;

public class UserIdentityImpl implements UserIdentity, HasSessionId, HasJwt, HasUuid {

    private final String userUuid;
    private final String id;
    private final HttpSession httpSession;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile TokenResponse tokenResponse;
    private volatile JwtClaims jwtClaims;

    public UserIdentityImpl(final String userUuid,
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
    public String getJwt() {
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
    public Optional<String> getFullName() {
        return Optional.ofNullable(jwtClaims)
                .flatMap(jwtClaims2 ->
                        JwtUtil.getClaimValue(jwtClaims2, OpenId.CLAIM__NAME));
    }

    @Override
    public String getPreferredUsername() {
        return Optional.ofNullable(jwtClaims)
                .flatMap(jwtClaims2 ->
                        JwtUtil.getClaimValue(jwtClaims2, OpenId.CLAIM__PREFERRED_USERNAME))
                .orElseGet(this::getId);
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
        return "UserIdentityImpl{" +
                "userUuid='" + userUuid + '\'' +
                ", id='" + id + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", preferredUsername='" + getPreferredUsername() + '\'' +
                '}';
    }
}
