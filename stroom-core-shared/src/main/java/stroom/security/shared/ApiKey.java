package stroom.security.shared;

import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.UserName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ApiKey implements HasAuditInfoGetters, HasIntegerId {

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final int version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final UserName owner;
    @JsonProperty
    private final String apiKey;
    @JsonProperty
    private final Long expireTimeMs;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String comments;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public ApiKey(@JsonProperty("id") final Integer id,
                  @JsonProperty("version") final int version,
                  @JsonProperty("createTimeMs") final Long createTimeMs,
                  @JsonProperty("createUser") final String createUser,
                  @JsonProperty("updateTimeMs") final Long updateTimeMs,
                  @JsonProperty("updateUser") final String updateUser,
                  @JsonProperty("owner") final UserName owner,
                  @JsonProperty("apiKey") final String apiKey,
                  @JsonProperty("expireTimeMs") final Long expireTimeMs,
                  @JsonProperty("name") final String name,
                  @JsonProperty("comments") final String comments,
                  @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.owner = owner;
        this.apiKey = apiKey;
        this.expireTimeMs = expireTimeMs;
        this.name = name;
        this.comments = comments;
        this.enabled = enabled;
    }

    private ApiKey(final Builder builder) {
        id = builder.id;
        version = builder.version;
        createTimeMs = builder.createTimeMs;
        createUser = builder.createUser;
        updateTimeMs = builder.updateTimeMs;
        updateUser = builder.updateUser;
        owner = builder.owener;
        apiKey = builder.apiKey;
        expireTimeMs = builder.expireTimeMs;
        name = builder.name;
        comments = builder.comments;
        enabled = builder.enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final ApiKey copy) {
        Builder builder = new Builder();
        builder.id = copy.getId();
        builder.version = copy.getVersion();
        builder.createTimeMs = copy.getCreateTimeMs();
        builder.createUser = copy.getCreateUser();
        builder.updateTimeMs = copy.getUpdateTimeMs();
        builder.updateUser = copy.getUpdateUser();
        builder.owener = copy.getOwner();
        builder.apiKey = copy.getApiKey();
        builder.expireTimeMs = copy.getExpireTimeMs();
        builder.name = copy.getName();
        builder.comments = copy.getComments();
        builder.enabled = copy.getEnabled();
        return builder;
    }

    @Override
    public Integer getId() {
        return null;
    }

    @Override
    public Long getCreateTimeMs() {
        return null;
    }

    @Override
    public String getCreateUser() {
        return null;
    }

    @Override
    public Long getUpdateTimeMs() {
        return null;
    }

    @Override
    public String getUpdateUser() {
        return null;
    }

    public int getVersion() {
        return version;
    }

    public UserName getOwner() {
        return owner;
    }

    public String getApiKey() {
        return apiKey;
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


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Integer id;
        private int version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private UserName owener;
        private String apiKey;
        private Long expireTimeMs;
        private String name;
        private String comments;
        private boolean enabled;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withId(final Integer val) {
            id = val;
            return this;
        }

        public Builder withVersion(final int val) {
            version = val;
            return this;
        }

        /**
         * Stamp the builder with the audit info
         */
        public Builder stamp(final HasAuditableUserIdentity hasAuditableUserIdentity) {
            final long now = System.currentTimeMillis();
            createTimeMs = now;
            createUser = hasAuditableUserIdentity.getUserIdentityForAudit();
            updateTimeMs = now;
            updateUser = hasAuditableUserIdentity.getUserIdentityForAudit();
            return this;
        }

        public Builder withCreateTimeMs(final long val) {
            createTimeMs = val;
            return this;
        }

        public Builder withCreateUser(final String val) {
            createUser = val;
            return this;
        }

        public Builder withUpdateTimeMs(final long val) {
            updateTimeMs = val;
            return this;
        }

        public Builder withUpdateUser(final String val) {
            updateUser = val;
            return this;
        }

        public Builder withOwner(final UserName val) {
            owener = val;
            return this;
        }

        public Builder withApiKey(final String val) {
            apiKey = val;
            return this;
        }

        public Builder withExpireTimeMs(final long val) {
            expireTimeMs = val;
            return this;
        }

        public Builder withExpireTimeMs(final Instant val) {
            expireTimeMs = Objects.requireNonNull(val).toEpochMilli();
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

        public ApiKey build() {
            return new ApiKey(this);
        }
    }
}
