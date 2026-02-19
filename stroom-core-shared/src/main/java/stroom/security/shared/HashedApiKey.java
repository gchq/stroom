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

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.HasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;
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

//    @SerialisationTestConstructor
//    private HashedApiKey() {
//        this(HashedApiKey
//                .builder()
//                .hashAlgorithm(HashAlgorithm.BCRYPT));
//    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
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


    public static final class Builder
            extends AbstractBuilder<HashedApiKey, HashedApiKey.Builder>
            implements HasAuditInfoBuilder<HashedApiKey, HashedApiKey.Builder> {

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

        private Builder(final HashedApiKey hashedApiKey) {
            id = hashedApiKey.id;
            version = hashedApiKey.version;
            createTimeMs = hashedApiKey.createTimeMs;
            createUser = hashedApiKey.createUser;
            updateTimeMs = hashedApiKey.updateTimeMs;
            updateUser = hashedApiKey.updateUser;
            owner = hashedApiKey.owner;
            apiKeyHash = hashedApiKey.apiKeyHash;
            apiKeyPrefix = hashedApiKey.apiKeyPrefix;
            expireTimeMs = hashedApiKey.expireTimeMs;
            name = hashedApiKey.name;
            comments = hashedApiKey.comments;
            enabled = hashedApiKey.enabled;
            hashAlgorithm = hashedApiKey.hashAlgorithm;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final int version) {
            this.version = version;
            return self();
        }

        @Override
        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        @Override
        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        @Override
        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        @Override
        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        public Builder owner(final UserRef owner) {
            this.owner = owner;
            return self();
        }

        public Builder apiKeyHash(final String apiKeyHash) {
            this.apiKeyHash = apiKeyHash;
            return self();
        }

        public Builder apiKeyPrefix(final String apiKeyPrefix) {
            this.apiKeyPrefix = apiKeyPrefix;
            return self();
        }

        public Builder expireTimeMs(final long expireTimeMs) {
            this.expireTimeMs = expireTimeMs;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder comments(final String comments) {
            this.comments = comments;
            return self();
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder hashAlgorithm(final HashAlgorithm hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public HashedApiKey build() {
            return new HashedApiKey(
                    id,
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
    }
}
