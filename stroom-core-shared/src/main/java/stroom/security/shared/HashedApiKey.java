/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.shared;

import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.SerialisationTestConstructor;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * A hashed API key. The actual API key is not persisted.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class HashedApiKey implements HasAuditInfoGetters, HasIntegerId {

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
    private final UserRef owner;
    @JsonProperty
    private final String apiKeyHash;
    @JsonProperty
    private final String apiKeyPrefix;
    @JsonProperty
    private final Long expireTimeMs;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String comments;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final HashAlgorithm hashAlgorithm;

    @JsonCreator
    public HashedApiKey(@JsonProperty("id") final Integer id,
                        @JsonProperty("version") final int version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("owner") final UserRef owner,
                        @JsonProperty("apiKeyHash") final String apiKeyHash,
                        @JsonProperty("apiKeyPrefix") final String apiKeyPrefix,
                        @JsonProperty("expireTimeMs") final Long expireTimeMs,
                        @JsonProperty("name") final String name,
                        @JsonProperty("comments") final String comments,
                        @JsonProperty("enabled") final boolean enabled,
                        @JsonProperty("hashAlgorithm") final HashAlgorithm hashAlgorithm) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.owner = owner;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.expireTimeMs = expireTimeMs;
        this.name = name;
        this.comments = comments;
        this.enabled = enabled;
        this.hashAlgorithm = Objects.requireNonNull(hashAlgorithm);
    }

    @SerialisationTestConstructor
    private HashedApiKey() {
        this(HashedApiKey
                .builder()
                .withHashAlgorithm(HashAlgorithm.BCRYPT));
    }

    private HashedApiKey(final Builder builder) {
        id = builder.id;
        version = builder.version;
        createTimeMs = builder.createTimeMs;
        createUser = builder.createUser;
        updateTimeMs = builder.updateTimeMs;
        updateUser = builder.updateUser;
        owner = builder.owner;
        apiKeyHash = builder.apiKeyHash;
        apiKeyPrefix = builder.apiKeyPrefix;
        expireTimeMs = builder.expireTimeMs;
        name = builder.name;
        comments = builder.comments;
        enabled = builder.enabled;
        hashAlgorithm = builder.hashAlgorithm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final HashedApiKey copy) {
        final Builder builder = new Builder();
        builder.id = copy.getId();
        builder.version = copy.getVersion();
        builder.createTimeMs = copy.getCreateTimeMs();
        builder.createUser = copy.getCreateUser();
        builder.updateTimeMs = copy.getUpdateTimeMs();
        builder.updateUser = copy.getUpdateUser();
        builder.owner = copy.getOwner();
        builder.apiKeyHash = copy.getApiKeyHash();
        builder.apiKeyPrefix = copy.getApiKeyPrefix();
        builder.expireTimeMs = copy.getExpireTimeMs();
        builder.name = copy.getName();
        builder.comments = copy.getComments();
        builder.enabled = copy.getEnabled();
        builder.hashAlgorithm = copy.getHashAlgorithm();
        return builder;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public int getVersion() {
        return version;
    }

    public UserRef getOwner() {
        return owner;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
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

    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final HashedApiKey that = (HashedApiKey) object;
        return version == that.version && enabled == that.enabled && Objects.equals(id,
                that.id) && Objects.equals(createTimeMs,
                that.createTimeMs) && Objects.equals(createUser, that.createUser) && Objects.equals(
                updateTimeMs,
                that.updateTimeMs) && Objects.equals(updateUser, that.updateUser) && Objects.equals(
                owner,
                that.owner) && Objects.equals(apiKeyHash, that.apiKeyHash) && Objects.equals(
                apiKeyPrefix,
                that.apiKeyPrefix) && Objects.equals(expireTimeMs, that.expireTimeMs) && Objects.equals(
                name,
                that.name) && Objects.equals(comments, that.comments) && hashAlgorithm == that.hashAlgorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                owner,
                apiKeyHash,
                apiKeyPrefix,
                expireTimeMs,
                name,
                comments,
                enabled,
                hashAlgorithm);
    }

    @Override
    public String toString() {
        return "ApiKey{" +
               "id=" + id +
               ", version=" + version +
               ", owner=" + owner +
               ", apiKeyHash='" + apiKeyHash + '\'' +
               ", apiKeyPrefix='" + apiKeyPrefix + '\'' +
               ", expireTimeMs=" + expireTimeMs + '\'' +
               ", name='" + name + '\'' +
               ", enabled=" + enabled +
               ", hashAlgorithm=" + hashAlgorithm +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Integer id;
        private int version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private UserRef owner;
        private String apiKeyHash;
        private String apiKeyPrefix;
        private Long expireTimeMs;
        private String name;
        private String comments;
        private boolean enabled = true;
        private HashAlgorithm hashAlgorithm = HashAlgorithm.DEFAULT;

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

        public Builder withOwner(final UserRef val) {
            owner = val;
            return this;
        }

        public Builder withApiKeyHash(final String val) {
            apiKeyHash = val;
            return this;
        }

        public Builder withApiKeyPrefix(final String val) {
            apiKeyPrefix = val;
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

        public Builder withHashAlgorithm(final HashAlgorithm val) {
            hashAlgorithm = val;
            return this;
        }

        public HashedApiKey build() {
            return new HashedApiKey(this);
        }
    }
}
