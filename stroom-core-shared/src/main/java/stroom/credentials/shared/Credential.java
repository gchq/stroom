/*
 * Copyright 2025 Crown Copyright
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
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasAuditInfoGetters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Credentials as stored by the Credentials storage system.
 * This class represents the metadata about the credentials. The secrets
 * are in the 'secret' field.
 * Initial use of this is in GitRepo.
 */
@JsonPropertyOrder({
        "uuid",
        "name",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "credentialType",
        "keyStoreType",
        "keyStorePath",
        "expiryTimeMs",
        "secret"
})
@JsonInclude(Include.NON_NULL)
public class Credential implements HasAuditInfoGetters, HasDisplayValue {

    /**
     * A sort-of type for this object. Not really a Document but handy to pretend it
     * is sometimes, so it has a TYPE field.
     */
    public static final String TYPE = "Credential";

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final CredentialType credentialType;
    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePath;
    @JsonProperty
    private final Long expiryTimeMs;

    /// Constructor for credentials with no key store file path, i.e. any type other than
    /// [CredentialType#KEY_STORE_FILE].
    ///
    /// @param uuid           UUID.
    /// @param name           Name.
    /// @param createTimeMs   Time in ms since epoch when this was created.
    /// @param updateTimeMs   Time in ms since epoch when this was last updated.
    /// @param createUser     The user that created this.
    /// @param updateUser     The user that last updated this.
    /// @param credentialType Type. If null then [CredentialType#USERNAME_PASSWORD] is assigned.
    /// @param keyStoreType   The type of key store, for key store credentials only.
    /// @param expiryTimeMs   Time in ms since epoch when this expires.
    public Credential(final String uuid,
                      final String name,
                      final Long createTimeMs,
                      final Long updateTimeMs,
                      final String createUser,
                      final String updateUser,
                      final CredentialType credentialType,
                      final KeyStoreType keyStoreType,
                      final Long expiryTimeMs) {
        this(uuid,
                name,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser,
                credentialType,
                keyStoreType,
                null,
                expiryTimeMs);
    }

    /// Constructor.
    ///
    /// @param uuid           UUID.
    /// @param name           Name.
    /// @param createTimeMs   Time in ms since epoch when this was created.
    /// @param updateTimeMs   Time in ms since epoch when this was last updated.
    /// @param createUser     The user that created this.
    /// @param updateUser     The user that last updated this.
    /// @param credentialType Type. If null then [CredentialType#USERNAME_PASSWORD] is assigned.
    /// @param keyStoreType   The type of key store, for key store credentials only.
    /// @param keyStorePath   The path of the key store file, for key store file credentials only.
    ///                       This is not secret so can be shown.
    /// @param expiryTimeMs   Time in ms since epoch when this expires.
    @JsonCreator
    public Credential(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("credentialType") final CredentialType credentialType,
            @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
            @JsonProperty("keyStorePath") final String keyStorePath,
            @JsonProperty("expiryTimeMs") final Long expiryTimeMs) {
        this.uuid = uuid;
        this.name = name;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.credentialType = credentialType == null
                ? CredentialType.USERNAME_PASSWORD
                : credentialType;
        this.keyStoreType = keyStoreType;
        this.keyStorePath = keyStorePath;
        this.expiryTimeMs = expiryTimeMs;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    /// @return The path of the key store file, for key store file credentials only, otherwise null.
    public String getKeyStorePath() {
        return keyStorePath;
    }

    public Long getExpiryTimeMs() {
        return expiryTimeMs;
    }

    public DocRef asDocRef() {
        return new DocRef(TYPE, uuid, name);
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Credential that = (Credential) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        return "Credential{" +
               "uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", createTimeMs=" + createTimeMs +
               ", updateTimeMs=" + updateTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateUser='" + updateUser + '\'' +
               ", credentialType=" + credentialType +
               ", keyStoreType=" + keyStoreType +
               ", keyStorePath='" + keyStorePath + '\'' +
               ", expiryTimeMs=" + expiryTimeMs +
               '}';
    }
}
