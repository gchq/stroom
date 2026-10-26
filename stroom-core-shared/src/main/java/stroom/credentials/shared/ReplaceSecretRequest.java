/*
 * Copyright 2026 Crown Copyright
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

package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/// Request to replace the secret held by an existing credential.
///
/// The old secret is discarded. Secrets are never sent back to the client, so the only way
/// to change one is to replace it whole. The expiry is set at the same time because it
/// describes the secret rather than the credential.
@JsonPropertyOrder({
        "uuid",
        "secret",
        "expiryTimeMs"
})
@JsonInclude(Include.NON_NULL)
public class ReplaceSecretRequest {

    @JsonProperty
    private final String uuid;

    @JsonProperty
    private final Secret secret;

    @JsonProperty
    private final Long expiryTimeMs;

    /// @param uuid         The UUID of the credential whose secret is to be replaced.
    /// @param secret       The new secret. Must be of the same type as the credential.
    /// @param expiryTimeMs When the new secret expires, or null if it does not expire.
    @JsonCreator
    public ReplaceSecretRequest(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("secret") final Secret secret,
            @JsonProperty("expiryTimeMs") final Long expiryTimeMs) {
        this.uuid = uuid;
        this.secret = secret;
        this.expiryTimeMs = expiryTimeMs;
    }

    /// @return The UUID of the credential whose secret is to be replaced.
    public String getUuid() {
        return uuid;
    }

    /// @return The new secret.
    public Secret getSecret() {
        return secret;
    }

    /// @return When the new secret expires, or null if it does not expire.
    public Long getExpiryTimeMs() {
        return expiryTimeMs;
    }
}
