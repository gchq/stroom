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

import stroom.util.shared.SerialisationTestConstructor;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CreateHashedApiKeyRequest {

    @JsonProperty
    private final UserRef owner;
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
    public CreateHashedApiKeyRequest(@JsonProperty("owner") final UserRef owner,
                                     @JsonProperty("expireTimeMs") final Long expireTimeMs,
                                     @JsonProperty("name") final String name,
                                     @JsonProperty("comments") final String comments,
                                     @JsonProperty("enabled") final boolean enabled,
                                     @JsonProperty("hashAlgorithm") final HashAlgorithm hashAlgorithm) {
        this.owner = Objects.requireNonNull(owner);
        this.expireTimeMs = expireTimeMs;
        this.name = Objects.requireNonNull(name);
        this.comments = comments;
        this.enabled = enabled;
        this.hashAlgorithm = Objects.requireNonNull(hashAlgorithm);
    }

    @SerialisationTestConstructor
    private CreateHashedApiKeyRequest() {
        this(CreateHashedApiKeyRequest
                .builder()
                .withOwner(UserRef.builder().build())
                .withName("test")
                .withHashAlgorithm(HashAlgorithm.BCRYPT));
    }

    private CreateHashedApiKeyRequest(final Builder builder) {
        owner = builder.owner;
        expireTimeMs = builder.expireTimeMs;
        name = builder.name;
        comments = builder.comments;
        enabled = builder.enabled;
        hashAlgorithm = builder.hashAlgorithm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final CreateHashedApiKeyRequest copy) {
        final Builder builder = new Builder();
        builder.owner = copy.getOwner();
        builder.expireTimeMs = copy.getExpireTimeMs();
        builder.name = copy.getName();
        builder.comments = copy.getComments();
        builder.enabled = copy.getEnabled();
        builder.hashAlgorithm = copy.getHashAlgorithm();
        return builder;
    }

    public UserRef getOwner() {
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
        final CreateHashedApiKeyRequest request = (CreateHashedApiKeyRequest) object;
        return enabled == request.enabled && Objects.equals(owner, request.owner) && Objects.equals(
                expireTimeMs,
                request.expireTimeMs) && Objects.equals(name, request.name) && Objects.equals(comments,
                request.comments) && hashAlgorithm == request.hashAlgorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, expireTimeMs, name, comments, enabled, hashAlgorithm);
    }

    @Override
    public String toString() {
        return "CreateApiKeyRequest{" +
               "owner=" + owner +
               ", expireTimeMs=" + expireTimeMs +
               ", name='" + name + '\'' +
               ", comments='" + comments + '\'' +
               ", enabled=" + enabled +
               ", hashAlgorithm=" + hashAlgorithm +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private UserRef owner;
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

        public Builder withOwner(final UserRef val) {
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

        public Builder withHashAlgorithm(final HashAlgorithm val) {
            hashAlgorithm = val;
            return this;
        }

        public CreateHashedApiKeyRequest build() {
            return new CreateHashedApiKeyRequest(this);
        }
    }
}
