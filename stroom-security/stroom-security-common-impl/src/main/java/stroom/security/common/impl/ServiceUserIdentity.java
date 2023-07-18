package stroom.security.common.impl;

import stroom.security.api.UserIdentity;

import java.util.Objects;

/**
 * User identity for a service user for this application to authenticate with other
 * applications on the same IDP realm. I.e. Stroom's processing user.
 * This user uses the client credentials flow.
 */
public class ServiceUserIdentity implements UserIdentity, HasJwtClaims, HasUpdatableToken {

    private final String id;
    private final String displayName;
    private final UpdatableToken updatableToken;

    public ServiceUserIdentity(final String id,
                               final String displayName,
                               final UpdatableToken updatableToken) {
        this.id = Objects.requireNonNull(id);
        // Fall back to id if not present
        this.displayName = Objects.requireNonNullElse(displayName, id);
        this.updatableToken = Objects.requireNonNull(updatableToken);
    }

    @Override
    public String getSubjectId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public UpdatableToken getUpdatableToken() {
        return updatableToken;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ServiceUserIdentity that = (ServiceUserIdentity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ServiceUserIdentity{" +
                "id='" + id + '\'' +
                ", updatableToken=" + updatableToken +
                '}';
    }
}
