package stroom.security.shared;

import stroom.util.shared.UserName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CreateApiKeyRequest {

    @JsonProperty
    private final UserName owner;
    @JsonProperty
    private final Long expireTimeMs;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String comments;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("owner") final UserName owner,
                               @JsonProperty("expireTimeMs") final Long expireTimeMs,
                               @JsonProperty("name") final String name,
                               @JsonProperty("comments") final String comments,
                               @JsonProperty("enabled") final boolean enabled) {
        this.owner = owner;
        this.expireTimeMs = expireTimeMs;
        this.name = name;
        this.comments = comments;
        this.enabled = enabled;
    }

    private CreateApiKeyRequest(final Builder builder) {
        owner = builder.owner;
        expireTimeMs = builder.expireTimeMs;
        name = builder.name;
        comments = builder.comments;
        enabled = builder.enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final CreateApiKeyRequest copy) {
        Builder builder = new Builder();
        builder.owner = copy.getOwner();
        builder.expireTimeMs = copy.getExpireTimeMs();
        builder.name = copy.getName();
        builder.comments = copy.getComments();
        builder.enabled = copy.getEnabled();
        return builder;
    }

    public UserName getOwner() {
        return owner;
    }

    public Long getExpireTimeMs() {
        return expireTimeMs;
    }

    public String getName() {
        return name;
    }

    public String getComments() {
        return comments;
    }

    public boolean getEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CreateApiKeyRequest that = (CreateApiKeyRequest) object;
        return enabled == that.enabled && Objects.equals(owner, that.owner) && Objects.equals(
                expireTimeMs,
                that.expireTimeMs) && Objects.equals(name, that.name) && Objects.equals(comments,
                that.comments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, expireTimeMs, name, comments, enabled);
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
                "owner=" + owner +
                ", expireTimeMs=" + expireTimeMs +
                ", name='" + name + '\'' +
                ", comments='" + comments + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private UserName owner;
        private Long expireTimeMs;
        private String name;
        private String comments;
        private boolean enabled = true;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withOwner(final UserName val) {
            owner = val;
            return this;
        }

        public Builder withExpireTimeMs(final long val) {
            expireTimeMs = val;
            return this;
        }

        public Builder withName(final String val) {
            name = val;
            return this;
        }

        public Builder withComments(final String val) {
            comments = val;
            return this;
        }

        public Builder withEnabled(final boolean val) {
            enabled = val;
            return this;
        }

        public CreateApiKeyRequest build() {
            return new CreateApiKeyRequest(this);
        }
    }
}
