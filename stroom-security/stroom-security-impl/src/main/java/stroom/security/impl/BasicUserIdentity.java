package stroom.security.impl;

import stroom.docref.HasUuid;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class BasicUserIdentity implements UserIdentity, HasUuid {

    private final String userUuid;
    private final String id;

    public BasicUserIdentity(final String userUuid, final String id) {
        this.userUuid = userUuid;
        this.id = id;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BasicUserIdentity that = (BasicUserIdentity) o;
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, id);
    }
}
