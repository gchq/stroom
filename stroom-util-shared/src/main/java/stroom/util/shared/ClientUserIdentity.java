package stroom.util.shared;

import stroom.security.shared.UserIdentity;

@Deprecated
// Do not use this class, it exists purely for GWT compilation purposes.
public class ClientUserIdentity implements UserIdentity, SharedObject {
    public ClientUserIdentity() {
        // Require3d for GWT.
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public String getJws() {
        return null;
    }

    @Override
    public String getSessionId() {
        return null;
    }
}
