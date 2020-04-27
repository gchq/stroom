package stroom.security.impl;

import stroom.security.api.UserIdentity;

import java.util.Objects;

class UserIdentityImpl implements UserIdentity {
    private final String userUuid;
    private final String id;
    private final String jws;
    private final String sessionId;

    UserIdentityImpl(final String userUuid, final String id, final String jws, final String sessionId) {
        this.userUuid = userUuid;
        this.id = id;
        this.jws = jws;
        this.sessionId = sessionId;
    }

    public String getUserUuid() {
        return userUuid;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getJws() {
        return jws;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof UserIdentity)) return false;
        final UserIdentity that = (UserIdentity) o;
        return Objects.equals(id, that.getId()) &&
                Objects.equals(jws, that.getJws()) &&
                Objects.equals(sessionId, that.getSessionId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jws, sessionId);
    }

    @Override
    public String toString() {
        return getId();
    }
}