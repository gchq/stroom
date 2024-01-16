package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.security.shared.User;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import java.util.Objects;
import java.util.Optional;

public class BasicUserIdentity implements UserIdentity, HasStroomUserIdentity {

    private final String userUuid;
    private final String subjectId;
    private final String displayName;
    private final String fullName;

    public BasicUserIdentity(final String userUuid,
                             final String subjectId,
                             final String displayName,
                             final String fullName) {
        this.userUuid = Objects.requireNonNull(userUuid);
        this.subjectId = Objects.requireNonNull(subjectId);
        this.displayName = displayName;
        this.fullName = fullName;
    }

    public BasicUserIdentity(final UserName userName) {
        Objects.requireNonNull(userName);
        this.userUuid = userName.getUuid();
        this.subjectId = userName.getSubjectId(); // User.name is the unique identifier. User.id is the DB PK.
        this.displayName = userName.getDisplayName();
        this.fullName = userName.getFullName();
    }

    public BasicUserIdentity(final User user) {
        Objects.requireNonNull(user);
        this.userUuid = user.getUuid();
        this.subjectId = user.getSubjectId(); // User.name is the unique identifier. User.id is the DB PK.
        this.displayName = user.getDisplayName();
        this.fullName = user.getFullName();
    }

    @Override
    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public String getUuid() {
        return userUuid;
    }

    @Override
    public String getDisplayName() {
        return Objects.requireNonNullElse(displayName, subjectId);
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    @Override
    public UserName asUserName() {
        String displayName = getDisplayName();
        if (Objects.equals(displayName, subjectId)) {
            displayName = null;
        }
        return new SimpleUserName(
                subjectId,
                Objects.equals(displayName, subjectId)
                        ? null
                        : displayName,
                getFullName().orElse(null),
                userUuid);
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
        return Objects.equals(userUuid, that.userUuid) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, subjectId);
    }

    @Override
    public String toString() {
        return "BasicUserIdentity{" +
                "userUuid='" + userUuid + '\'' +
                ", id='" + subjectId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }
}
