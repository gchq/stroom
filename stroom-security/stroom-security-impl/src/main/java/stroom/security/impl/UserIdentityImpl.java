package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.shared.User;

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
    public String toString() {
        return getId();
    }
}