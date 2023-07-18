package stroom.security.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.HasJwtClaims;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.util.NullSafe;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

class ApiUserIdentity implements UserIdentity, HasSessionId, HasStroomUserIdentity, HasJwtClaims, HasJwt {

    private final String userUuid;
    private final String id;
    private final String displayName;
    private final String sessionId;
    private final JwtContext jwtContext;

    /**
     * @param userUuid    The stroom_user UUID
     * @param id          The unique ID on the IDP, i.e. the 'sub' claim
     * @param displayName
     */
    ApiUserIdentity(final String userUuid,
                    final String id,
                    final String displayName,
                    final String sessionId,
                    final JwtContext jwtContext) {
        this.userUuid = userUuid;
        this.id = id;
        // Fall back to id if not present
        this.displayName = Objects.requireNonNullElse(displayName, id);
        this.sessionId = sessionId;
        this.jwtContext = jwtContext;
    }

    @Override
    public String getSubjectId() {
        return id;
    }

    @Override
    public Optional<String> getFullName() {
        return getClaimValue("name");
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getUuid() {
        return userUuid;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getJwt() {
        return NullSafe.get(jwtContext, JwtContext::getJwt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ApiUserIdentity that = (ApiUserIdentity) o;
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(id,
                that.id) && Objects.equals(displayName, that.displayName) && Objects.equals(sessionId,
                that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, id, displayName, sessionId);
    }

    @Override
    public String toString() {
        return getSubjectId();
    }

    @Override
    public JwtClaims getJwtClaims() {
        return jwtContext.getJwtClaims();
    }
}
