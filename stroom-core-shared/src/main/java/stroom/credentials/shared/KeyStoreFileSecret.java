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

import stroom.ai.shared.KeyStoreType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// The secret part of credentials that refer to a key store file on the local file system,
/// e.g. one maintained by a key manager such as cert-manager.
///
/// Unlike [KeyStoreSecret] the key store itself is not held by Stroom. The file is read from
/// the same path on every node, so the path must be valid on all of them.
@JsonPropertyOrder({
        "keyStoreType",
        "keyStorePath",
        "keyStorePassword"
})
@JsonInclude(Include.NON_NULL)
public final class KeyStoreFileSecret implements Secret {

    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePath;
    @JsonProperty
    private final String keyStorePassword;

    /// @param keyStoreType     The type of the key store file, e.g. PKCS12.
    /// @param keyStorePath     The absolute path of the key store file on each node.
    /// @param keyStorePassword The password of the key store, or null if it has none.
    @JsonCreator
    public KeyStoreFileSecret(
            @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
            @JsonProperty("keyStorePath") final String keyStorePath,
            @JsonProperty("keyStorePassword") final String keyStorePassword) {
        this.keyStoreType = keyStoreType;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    /// @return The type of the key store file.
    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    /// @return The absolute path of the key store file on each node.
    public String getKeyStorePath() {
        return keyStorePath;
    }

    /// @return The password of the key store, or null if it has none.
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyStoreFileSecret that = (KeyStoreFileSecret) o;
        return keyStoreType == that.keyStoreType &&
               Objects.equals(keyStorePath, that.keyStorePath) &&
               Objects.equals(keyStorePassword, that.keyStorePassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStoreType, keyStorePath, keyStorePassword);
    }
}
