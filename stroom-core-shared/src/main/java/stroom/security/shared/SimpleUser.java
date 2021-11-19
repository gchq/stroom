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

    public SimpleUser() {
    }

    public SimpleUser(final User user) {
        this.uuid = user.getUuid();
        this.name = user.getName();
    }

    @JsonCreator
    public SimpleUser(@JsonProperty("name") final String name,
                      @JsonProperty("uuid") final String uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "SimpleUser{" +
                "name='" + name + '\'' +
                ", uuid='" + uuid + '\'' +
                '}';
    }

    public static final class Builder {

        private String name;
        private String uuid;

        private Builder() {
        }

        private Builder(final User user) {
            this.name = user.getName();
            this.uuid = user.getUuid();
        }

        private Builder(final SimpleUser user) {
            this.name = user.name;
            this.uuid = user.uuid;
        }

        public Builder name(final String value) {
            name = value;
            return this;
        }

        public Builder uuid(final String value) {
            uuid = value;
            return this;
        }

        public SimpleUser build() {
            return new SimpleUser(name, uuid);
        }
    }
}
