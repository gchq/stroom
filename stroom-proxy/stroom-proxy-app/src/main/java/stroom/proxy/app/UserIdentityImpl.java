package stroom.proxy.app;

import stroom.security.api.UserIdentity;

import java.util.Objects;

class UserIdentityImpl implements UserIdentity {

    private final String id;

    UserIdentityImpl(final String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserIdentity)) {
            return false;
        }
        final UserIdentity that = (UserIdentity) o;
        return Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getId();
    }
}
