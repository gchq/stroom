package stroom.security.server;

import stroom.security.shared.UserIdentity;
import stroom.security.shared.UserRef;

import java.util.Objects;

class UserIdentityImpl implements UserIdentity {
    private final UserRef userRef;
    private final String name;
    private final String jws;
    private final String sessionId;

    UserIdentityImpl(final UserRef userRef, final String name, final String jws, final String sessionId) {
        this.userRef = userRef;
        this.name = name;
        this.jws = jws;
        this.sessionId = sessionId;
    }

    public UserRef getUserRef() {
        return userRef;
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
        return Objects.equals(userRef, that.userRef) &&
                Objects.equals(name, that.name) &&
                Objects.equals(jws, that.jws) &&
                Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef, name, jws, sessionId);
    }

    @Override
    public String toString() {
        return getId();
    }
}