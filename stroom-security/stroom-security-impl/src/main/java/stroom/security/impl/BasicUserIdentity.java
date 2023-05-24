package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.security.shared.User;

import java.util.Objects;
import java.util.Optional;

public class BasicUserIdentity implements UserIdentity, HasStroomUserIdentity {

    private final String userUuid;
    private final String id;
    private final String displayName;
    private final String fullName;

    public BasicUserIdentity(final String userUuid,
                             final String id,
                             final String displayName,
                             final String fullName) {
        this.userUuid = Objects.requireNonNull(userUuid);
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName;
        this.fullName = fullName;
    }

    public BasicUserIdentity(final User user) {
        Objects.requireNonNull(user);
        this.userUuid = user.getUuid();
        this.id = user.getName(); // User.name is the unique identifier. User.id is the DB PK.
        this.displayName = user.getDisplayName();
        this.fullName = user.getFullName();
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
    public String getDisplayName() {
        return Objects.requireNonNullElse(displayName, id);
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.of(fullName);
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

    @Override
    public String toString() {
        return "BasicUserIdentity{" +
                "userUuid='" + userUuid + '\'' +
                ", id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }
}
