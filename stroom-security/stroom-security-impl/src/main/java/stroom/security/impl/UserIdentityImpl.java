package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.shared.User;

import java.util.Objects;

class UserIdentityImpl implements UserIdentity {
    private final User user;
    private final String name;
    private final String jws;
    private final String sessionId;

    UserIdentityImpl(final User user, final String name, final String jws, final String sessionId) {
        this.user = user;
        this.name = name;
        this.jws = jws;
        this.sessionId = sessionId;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String getId() {
        return name;
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
        if (!(o instanceof UserIdentityImpl)) return false;
        final UserIdentityImpl that = (UserIdentityImpl) o;
        return Objects.equals(user, that.user) &&
                Objects.equals(name, that.name) &&
                Objects.equals(jws, that.jws) &&
                Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, name, jws, sessionId);
    }

    @Override
    public String toString() {
        return getId();
    }
}