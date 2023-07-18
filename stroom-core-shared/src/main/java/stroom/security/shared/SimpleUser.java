package stroom.security.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SimpleUser {

    @JsonProperty
    private String subjectId;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private String displayName;
    @JsonProperty
    private String fullName;

    public SimpleUser() {
    }

    public SimpleUser(final User user) {
        this.uuid = user.getUuid();
        this.subjectId = user.getSubjectId();
        this.displayName = user.getDisplayName();
        this.fullName = user.getFullName();
    }

    @JsonCreator
    public SimpleUser(@JsonProperty("subjectId") final String subjectId,
                      @JsonProperty("uuid") final String uuid,
                      @JsonProperty("displayName") final String displayName,
                      @JsonProperty("fullName") final String fullName) {
        this.subjectId = subjectId;
        this.uuid = uuid;
        this.displayName = displayName;
        this.fullName = fullName;
    }

    /**
     * See {@link User#getSubjectId()}
     */
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * See {@link User#getSubjectId()}
     */
    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    /**
     * See {@link User#getUuid()}
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * See {@link User#getUuid()}
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * See {@link User#getDisplayName()}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * See {@link User#getDisplayName()}
     */
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    /**
     * See {@link User#getFullName()}
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * See {@link User#getFullName()}
     */
    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String toString() {
        return "SimpleUser{" +
                "name='" + subjectId + '\'' +
                ", uuid='" + uuid + '\'' +
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


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String subjectId;
        private String uuid;
        private String displayName;
        private String fullName;

        private Builder() {
        }

        private Builder(final User user) {
            this.subjectId = user.getSubjectId();
            this.uuid = user.getUuid();
            this.displayName = user.getDisplayName();
            this.fullName = user.getFullName();
        }

        private Builder(final SimpleUser user) {
            this.subjectId = user.subjectId;
            this.uuid = user.uuid;
            this.displayName = user.getDisplayName();
            this.fullName = user.getFullName();
        }

        public Builder subjectId(final String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
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

        public SimpleUser build() {
            return new SimpleUser(subjectId, uuid, displayName, fullName);
        }
    }
}
