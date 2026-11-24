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

package stroom.credentials.impl;

import stroom.ai.shared.KeyStoreType;
import stroom.util.shared.IsAtomicConfigValue;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/// A 'Key Store File' credential defined in config, that is created at start-up if no credential
/// with its name exists. Held as a list in [CredentialsConfig#getKeyStoreFiles()].
@NotInjectableConfig
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"name", "keyStoreType", "keyStorePath", "keyStorePassword"})
public class KeyStoreFileCredentialConfig implements IsAtomicConfigValue {

    public static final KeyStoreType DEFAULT_KEY_STORE_TYPE = KeyStoreType.PKCS12;

    @JsonProperty
    private final String name;
    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePath;
    @JsonProperty
    private final String keyStorePassword;

    /// @param name             The name of the credential.
    /// @param keyStoreType     The type of the key store file. Defaults to PKCS12 if null.
    /// @param keyStorePath     The absolute path of the key store file.
    /// @param keyStorePassword The pass phrase of the key store file, or null if it has none.
    @JsonCreator
    public KeyStoreFileCredentialConfig(@JsonProperty("name") final String name,
                                        @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
                                        @JsonProperty("keyStorePath") final String keyStorePath,
                                        @JsonProperty("keyStorePassword") final String keyStorePassword) {
        this.name = name;
        this.keyStoreType = Objects.requireNonNullElse(keyStoreType, DEFAULT_KEY_STORE_TYPE);
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    /// @return The name of the credential.
    @NotBlank(message = "Each key store file credential must have a name")
    public String getName() {
        return name;
    }

    /// @return The type of the key store file.
    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    /// @return The absolute path of the key store file.
    @NotBlank(message = "Each key store file credential must have a keyStorePath")
    public String getKeyStorePath() {
        return keyStorePath;
    }

    /// @return The pass phrase of the key store file, or null if it has none.
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyStoreFileCredentialConfig that = (KeyStoreFileCredentialConfig) o;
        return Objects.equals(name, that.name)
               && keyStoreType == that.keyStoreType
               && Objects.equals(keyStorePath, that.keyStorePath)
               && Objects.equals(keyStorePassword, that.keyStorePassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, keyStoreType, keyStorePath, keyStorePassword);
    }

    // The pass phrase is never included so that it can't end up in a log
    @Override
    public String toString() {
        return "KeyStoreFileCredentialConfig{" +
               "name='" + name + '\'' +
               ", keyStoreType=" + keyStoreType +
               ", keyStorePath='" + keyStorePath + '\'' +
               ", keyStorePassword=" + (keyStorePassword == null
                ? "null"
                : "'********'") +
               '}';
    }
}
