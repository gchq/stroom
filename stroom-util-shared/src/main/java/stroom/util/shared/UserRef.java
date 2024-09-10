package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "uuid",
        "subjectId",
        "displayName",
        "fullName",
        "group"
})
public final class UserRef {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;
    @JsonProperty
    private final boolean group;

    @JsonCreator
    public UserRef(@JsonProperty("uuid") final String uuid,
                   @JsonProperty("subjectId") final String subjectId,
                   @JsonProperty("displayName") final String displayName,
                   @JsonProperty("fullName") final String fullName,
                   @JsonProperty("group") final boolean group) {
        Objects.requireNonNull(uuid, "Null uuid provided to UserRef");
        this.uuid = uuid;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.fullName = fullName;
        this.group = group;
    }

    public String getUuid() {
        return uuid;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isGroup() {
        return group;
    }

    /**
     * @return "Group" or "User" depending on type.
     */
    @JsonIgnore
    public String getType() {
        return group
                ? "Group"
                : "User";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserRef userRef = (UserRef) o;
        return Objects.equals(uuid, userRef.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    public String toDebugString() {
        return "UserRef{" +
                "uuid='" + uuid + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fullName='" + fullName + '\'' +
                ", group=" + group +
                '}';
    }

    public String toDisplayString() {
        if (displayName != null) {
            return displayName;
        } else if (subjectId != null) {
            return subjectId;
        } else if (fullName != null) {
            return fullName;
        } else {
            return "{" + uuid + "}";
        }
    }

    public String toInfoString() {
        final StringBuilder sb = new StringBuilder();
        if (displayName != null) {
            sb.append(displayName);
        } else if (subjectId != null) {
            sb.append(subjectId);
        } else if (fullName != null) {
            sb.append(fullName);
        }
        if (uuid != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("{");
            sb.append(uuid);
            sb.append("}");
        }

        if (sb.length() > 0) {
            return sb.toString();
        }

        return toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String uuid;
        private String subjectId;
        private String displayName;
        private String fullName;
        private boolean group;

        private Builder() {
        }

        private Builder(final UserRef userRef) {
            this.uuid = userRef.uuid;
            this.subjectId = userRef.subjectId;
            this.displayName = userRef.displayName;
            this.fullName = userRef.fullName;
            this.group = userRef.group;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder subjectId(final String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder fullName(final String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder group(final boolean group) {
            this.group = group;
            return this;
        }

        public UserRef build() {
            Objects.requireNonNull(uuid, "Null UUID");
            return new UserRef(uuid, subjectId, displayName, fullName, group);
        }
    }
}
