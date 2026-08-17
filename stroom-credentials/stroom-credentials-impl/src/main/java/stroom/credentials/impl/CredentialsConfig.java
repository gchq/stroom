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
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class CredentialsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String DEFAULT_KEY_STORE_CACHE_PATH = "${stroom.home}/keystores";

    /**
     * Database config
     */
    private final CredentialsDbConfig dbConfig;
    private final String keyStoreCachePath;

    /**
     * Default constructor. Configuration created with default values.
     */
    public CredentialsConfig() {
        dbConfig = new CredentialsDbConfig();
        keyStoreCachePath = DEFAULT_KEY_STORE_CACHE_PATH;
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param dbConfig The DB configuration.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsConfig(@JsonProperty("db") final CredentialsDbConfig dbConfig,
                             @JsonProperty("keyStoreCachePath") final String keyStoreCachePath) {
        this.dbConfig = dbConfig;
        this.keyStoreCachePath = keyStoreCachePath;
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

    /**
     * DB configuration class.
     */

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialsConfig that = (CredentialsConfig) o;
        return Objects.equals(dbConfig, that.dbConfig) && Objects.equals(keyStoreCachePath,
                that.keyStoreCachePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbConfig, keyStoreCachePath);
    }

    @Override
    public String toString() {
        return "CredentialsConfig{" +
               "dbConfig=" + dbConfig +
               ", keyStoreCachePath='" + keyStoreCachePath + '\'' +
               '}';
    }
}
