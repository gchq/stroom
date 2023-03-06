package stroom.security.impl;

import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.HasJwtClaims;
import stroom.security.shared.HasStroomUserIdentity;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

class ApiUserIdentity implements UserIdentity, HasSessionId, HasStroomUserIdentity, HasJwtClaims {

    private final String userUuid;
    private final String id;
    private final String sessionId;
    private final JwtContext jwtContext;

    /**
     * @param userUuid The stroom_user UUID
     * @param id The unique ID on the IDP, i.e. the 'sub' claim
     */
    ApiUserIdentity(final String userUuid,
                    final String id,
                    final String sessionId,
                    final JwtContext jwtContext) {
        this.userUuid = userUuid;
        this.id = id;
        this.sessionId = sessionId;
        this.jwtContext = jwtContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Optional<String> getFullName() {
        return getClaimValue("name");
    }

    @Override
    public String getPreferredUsername() {
        return getClaimValue("preferred_username")
                .orElseGet(this::getId);
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ApiUserIdentity that = (ApiUserIdentity) o;
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(id,
                that.id) && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, id, sessionId);
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public JwtClaims getJwtClaims() {
        return jwtContext.getJwtClaims();
    }
}
