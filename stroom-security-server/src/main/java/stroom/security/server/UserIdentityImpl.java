package stroom.security.server;

import stroom.security.shared.UserIdentity;
import stroom.security.shared.UserRef;

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
    public String toString() {
        return getId();
    }
}