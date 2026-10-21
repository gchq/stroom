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

package stroom.credentials.impl;

import stroom.config.common.HasDbConfig;
import stroom.credentials.impl.db.CredentialsDbConfig;
import stroom.security.shared.User;
import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class CredentialsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String DEFAULT_KEY_STORE_CACHE_PATH = "${stroom.home}/keystores";
    public static final String DEFAULT_KEY_STORE_FILES_OWNER_SUBJECT_ID = User.ADMINISTRATORS_GROUP_SUBJECT_ID;
    public static final UserType DEFAULT_KEY_STORE_FILES_OWNER_TYPE = UserType.GROUP;

    /**
     * Database config
     */
    private final CredentialsDbConfig dbConfig;
    private final String keyStoreCachePath;
    private final List<String> allowedKeyStoreFileDirs;
    private final List<KeyStoreFileCredentialConfig> keyStoreFiles;
    private final String keyStoreFilesOwnerSubjectId;
    private final UserType keyStoreFilesOwnerType;

    /**
     * Default constructor. Configuration created with default values.
     */
    public CredentialsConfig() {
        dbConfig = new CredentialsDbConfig();
        keyStoreCachePath = DEFAULT_KEY_STORE_CACHE_PATH;
        allowedKeyStoreFileDirs = List.of();
        keyStoreFiles = List.of();
        keyStoreFilesOwnerSubjectId = DEFAULT_KEY_STORE_FILES_OWNER_SUBJECT_ID;
        keyStoreFilesOwnerType = DEFAULT_KEY_STORE_FILES_OWNER_TYPE;
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param dbConfig The DB configuration.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsConfig(@JsonProperty("db") final CredentialsDbConfig dbConfig,
                             @JsonProperty("keyStoreCachePath") final String keyStoreCachePath,
                             @JsonProperty("allowedKeyStoreFileDirs") final List<String> allowedKeyStoreFileDirs,
                             @JsonProperty("keyStoreFiles") final List<KeyStoreFileCredentialConfig> keyStoreFiles,
                             @JsonProperty("keyStoreFilesOwnerSubjectId") final String keyStoreFilesOwnerSubjectId,
                             @JsonProperty("keyStoreFilesOwnerType") final UserType keyStoreFilesOwnerType) {
        this.dbConfig = dbConfig;
        this.keyStoreCachePath = keyStoreCachePath;
        this.allowedKeyStoreFileDirs = NullSafe.stream(allowedKeyStoreFileDirs)
                .filter(dir -> !NullSafe.isBlankString(dir))
                .toList();
        this.keyStoreFiles = NullSafe.stream(keyStoreFiles)
                .filter(Objects::nonNull)
                .toList();
        this.keyStoreFilesOwnerSubjectId = Objects.requireNonNullElse(
                keyStoreFilesOwnerSubjectId, DEFAULT_KEY_STORE_FILES_OWNER_SUBJECT_ID);
        this.keyStoreFilesOwnerType = Objects.requireNonNullElse(
                keyStoreFilesOwnerType, DEFAULT_KEY_STORE_FILES_OWNER_TYPE);
    }

    @Override
    @JsonProperty("db")
    public CredentialsDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty
    @JsonPropertyDescription("The path to stored cached key stores.")
    public String getKeyStoreCachePath() {
        return keyStoreCachePath;
    }

    /// @return The directories that 'Key Store File' credentials may refer to files in.
    @JsonProperty
    @JsonPropertyDescription("The directories on the local file system that 'Key Store File' credentials "
                             + "may refer to key store files in, including any sub-directories. The same "
                             + "directories must be present on every node. If empty (the default) then "
                             + "'Key Store File' credentials cannot be created or used. Only list "
                             + "directories that hold key stores for Stroom to use as a client, never ones "
                             + "holding Stroom's own server key stores.")
    public List<String> getAllowedKeyStoreFileDirs() {
        return allowedKeyStoreFileDirs;
    }

    /// @return The 'Key Store File' credentials to create at start-up if they don't already exist.
    @Valid
    @Password
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("Should only be set in the application YAML config file. A list of 'Key Store "
                             + "File' credentials to create when Stroom starts, each with a 'name', "
                             + "'keyStoreType' (JCEKS, JKS or PKCS12, default PKCS12), 'keyStorePath' and "
                             + "optional 'keyStorePassword'. Use an environment variable, e.g. "
                             + "'${KEY_STORE_PASSWORD}', to keep the pass phrase out of the file. A credential "
                             + "is only created if none with its name exists. An existing credential is never "
                             + "changed, so later changes to an entry have no effect; use Replace Secret in "
                             + "the UI instead. A credential deleted in the UI is created again at the next "
                             + "start-up unless its entry is also removed from here. The file must exist at "
                             + "start-up and be in one of "
                             + "'allowedKeyStoreFileDirs'. Entries that can't be created are logged and "
                             + "skipped. The credentials are owned by 'keyStoreFilesOwnerSubjectId'.")
    public List<KeyStoreFileCredentialConfig> getKeyStoreFiles() {
        return keyStoreFiles;
    }

    /// @return The subject ID of the user, or name of the group, that will own the credentials
    /// created from [#getKeyStoreFiles()].
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("Should only be set in the application YAML config file. The user or group that "
                             + "will own the credentials created from 'keyStoreFiles'. It must already exist "
                             + "and have the 'Credentials' permission. For a group this is its name. For an "
                             + "Open ID Connect user this is the claim value that uniquely identifies the user "
                             + "on the IDP (often 'sub' or 'oid'), and the user only exists once they have "
                             + "logged in. Default is the 'Administrators' group.")
    public String getKeyStoreFilesOwnerSubjectId() {
        return keyStoreFilesOwnerSubjectId;
    }

    /// @return Whether [#getKeyStoreFilesOwnerSubjectId()] is a user or a group.
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("Should only be set in the application YAML config file. Whether "
                             + "'keyStoreFilesOwnerSubjectId' is a 'USER' or a 'GROUP'. Default is 'GROUP'.")
    public UserType getKeyStoreFilesOwnerType() {
        return keyStoreFilesOwnerType;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialsConfig that = (CredentialsConfig) o;
        return Objects.equals(dbConfig, that.dbConfig)
               && Objects.equals(keyStoreCachePath, that.keyStoreCachePath)
               && Objects.equals(allowedKeyStoreFileDirs, that.allowedKeyStoreFileDirs)
               && Objects.equals(keyStoreFiles, that.keyStoreFiles)
               && Objects.equals(keyStoreFilesOwnerSubjectId, that.keyStoreFilesOwnerSubjectId)
               && keyStoreFilesOwnerType == that.keyStoreFilesOwnerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbConfig,
                keyStoreCachePath,
                allowedKeyStoreFileDirs,
                keyStoreFiles,
                keyStoreFilesOwnerSubjectId,
                keyStoreFilesOwnerType);
    }

    @Override
    public String toString() {
        return "CredentialsConfig{" +
               "dbConfig=" + dbConfig +
               ", keyStoreCachePath='" + keyStoreCachePath + '\'' +
               ", allowedKeyStoreFileDirs=" + allowedKeyStoreFileDirs +
               ", keyStoreFiles=" + keyStoreFiles +
               ", keyStoreFilesOwnerSubjectId='" + keyStoreFilesOwnerSubjectId + '\'' +
               ", keyStoreFilesOwnerType=" + keyStoreFilesOwnerType +
               '}';
    }
}
