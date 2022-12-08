package stroom.receive.common;

import stroom.security.api.UserIdentity;

public class UnauthenticatedUserIdentity implements UserIdentity {

    private static final String ID = "UNAUTHENTICATED_USER";

    private static final UnauthenticatedUserIdentity INSTANCE = new UnauthenticatedUserIdentity();

    private UnauthenticatedUserIdentity() {
    }

    public static UnauthenticatedUserIdentity getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String toString() {
        return getId();
    }
}
