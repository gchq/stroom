package stroom.security.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SimpleUser {

    @JsonProperty
    private String name;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private String preferredUsername;
    @JsonProperty
    private String fullName;

    public SimpleUser() {
    }

    public SimpleUser(final User user) {
        this.uuid = user.getUuid();
        this.name = user.getName();
        this.preferredUsername = user.getPreferredUsername();
        this.fullName = user.getFullName();
    }

    @JsonCreator
    public SimpleUser(@JsonProperty("name") final String name,
                      @JsonProperty("uuid") final String uuid,
                      @JsonProperty("preferredUsername") final String preferredUsername,
                      @JsonProperty("fullName") final String fullName) {
        this.name = name;
        this.uuid = uuid;
        this.preferredUsername = preferredUsername;
        this.fullName = fullName;
    }

    /**
     * See {@link User#getName()}
     */
    public String getName() {
        return name;
    }

    /**
     * See {@link User#getName()}
     */
    public void setName(String name) {
        this.name = name;
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
     * See {@link User#getPreferredUsername()}
     */
    public String getPreferredUsername() {
        return preferredUsername;
    }

    /**
     * See {@link User#getPreferredUsername()}
     */
    public void setPreferredUsername(final String preferredUsername) {
        this.preferredUsername = preferredUsername;
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
                "name='" + name + '\'' +
                ", uuid='" + uuid + '\'' +
                ", preferredUsername='" + preferredUsername + '\'' +
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

        private String name;
        private String uuid;
        private String preferredUsername;
        private String fullName;

        private Builder() {
        }

        private Builder(final User user) {
            this.name = user.getName();
            this.uuid = user.getUuid();
            this.preferredUsername = user.getPreferredUsername();
            this.fullName = user.getFullName();
        }

        private Builder(final SimpleUser user) {
            this.name = user.name;
            this.uuid = user.uuid;
            this.preferredUsername = user.getPreferredUsername();
            this.fullName = user.getFullName();
        }

        public Builder name(final String value) {
            name = value;
            return this;
        }

        public Builder uuid(final String value) {
            uuid = value;
            return this;
        }

        public Builder preferredUsername(final String value) {
            preferredUsername = value;
            return this;
        }

        public Builder fullName(final String value) {
            fullName = value;
            return this;
        }

        public SimpleUser build() {
            return new SimpleUser(name, uuid, preferredUsername, fullName);
        }
    }
}
