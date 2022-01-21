package stroom.security.impl;

import stroom.docref.HasUuid;
import stroom.security.api.HasJws;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;

class ApiUserIdentity implements UserIdentity, HasSessionId, HasUuid, HasJws {

    private final String userUuid;
    private final String id;
    private final String sessionId;
    private final JwtContext jwtContext;

    ApiUserIdentity(final String userUuid,
                    final String id,
                    final String sessionId,
                    final JwtContext jwtContext) {
        this.userUuid = userUuid;
        this.id = id;
        this.sessionId = sessionId;
        this.jwtContext = jwtContext;
    }

    public String getUserUuid() {
        return userUuid;
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
        return jwtContext.getJwt();
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
}
