package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "subjectId",
        "displayName",
        "fullName"
})
public class UserDesc {

    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;

    @JsonCreator
    public UserDesc(@JsonProperty("subjectId") final String subjectId,
                    @JsonProperty("displayName") final String displayName,
                    @JsonProperty("fullName") final String fullName) {
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.fullName = fullName;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserDesc that = (UserDesc) o;
        return Objects.equals(subjectId, that.subjectId) && Objects.equals(displayName,
                that.displayName) && Objects.equals(fullName, that.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, displayName, fullName);
    }

    @Override
    public String toString() {
        return "ExternalUser{" +
                "subjectId='" + subjectId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String subjectId;
        private String displayName;
        private String fullName;

        private Builder() {
        }

        private Builder(final UserDesc externalUser) {
            this.subjectId = externalUser.subjectId;
            this.displayName = externalUser.displayName;
            this.fullName = externalUser.fullName;
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

        public UserDesc build() {
            return new UserDesc(subjectId, displayName, fullName);
        }
    }
}
