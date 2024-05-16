package stroom.util.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

/**
 * Pojo for representing enough info to identify a user
 */
@JsonInclude(Include.NON_NULL)
public class SimpleUserName implements Comparable<UserName>, HasAuditableUserIdentity, UserName {

    private static final Comparator<UserName> COMPARATOR = Comparator.comparing(UserName::getSubjectId);

    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final boolean group;

    @JsonCreator
    public SimpleUserName(@JsonProperty("subjectId") final String subjectId,
                          @JsonProperty("displayName") final String displayName,
                          @JsonProperty("fullName") final String fullName,
                          @JsonProperty("uuid") final String uuid,
                          @JsonProperty("group") final boolean group) {

        if (GwtNullSafe.isBlankString(subjectId)) {
            throw new RuntimeException("subjectId must have a value");
        }
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.fullName = fullName;
        this.uuid = uuid;
        this.group = group;
    }

    public SimpleUserName(final String subjectId,
                          final String displayName,
                          final String fullName,
                          final boolean group) {
        this(subjectId, displayName, fullName, null, group);
    }

    /**
     * Creates a {@link SimpleUserName} representing a user.
     */
    public static SimpleUserName fromUserSubjectId(final String subjectId) {
        return new SimpleUserName(subjectId, null, null, false);
    }

    /**
     * Creates a {@link SimpleUserName} representing a group.
     */
    public static SimpleUserName fromGroupName(final String name) {
        return new SimpleUserName(name, null, null, true);
    }

    @Override
    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean isGroup() {
        return group;
    }

    @Override
    @JsonIgnore
    public String getUserIdentityForAudit() {
        return HasAuditableUserIdentity.fromUserNames(subjectId, displayName);
    }

    @Override
    public String toString() {
        return UserName.buildCombinedName(this);
    }

    /**
     * displayName and fullName are optional and for decoration, so equals/hash/compare
     * is done on name only
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SimpleUserName userName = (SimpleUserName) o;
        return subjectId.equals(userName.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(final UserName o) {
        return COMPARATOR.compare(this, o);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String subjectId = null;
        private String displayName = null;
        private String fullName = null;
        private String uuid = null;
        private boolean group = false;

        private Builder() {
        }

        private Builder(final SimpleUserName username) {
            this.subjectId = username.subjectId;
            this.displayName = username.displayName;
            this.fullName = username.fullName;
            this.uuid = username.uuid;
            this.group = username.group;
        }

        public Builder subjectId(final String value) {
            subjectId = value;
            return this;
        }

        public Builder displayName(final String value) {
            displayName = value;
            return this;
        }

        public Builder fullName(final String value) {
            fullName = value;
            return this;
        }

        public Builder uuid(final String value) {
            uuid = value;
            return this;
        }

        public Builder group(final boolean isGroup) {
            group = isGroup;
            return this;
        }

        public UserName build() {
            return new SimpleUserName(subjectId, displayName, fullName, uuid, group);
        }
    }
}
