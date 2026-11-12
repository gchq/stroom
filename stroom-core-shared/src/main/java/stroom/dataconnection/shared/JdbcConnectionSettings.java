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

package stroom.dataconnection.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;

/// The settings a [DataConnectionDoc] uses to reach a database through a JDBC driver.
///
/// The credential is held by name only. It is resolved on the server at the point of use, so this
/// object is safe to send to the browser and to include in exports.
@JsonPropertyOrder({
        "url",
        "credentialName",
        "connectionProperties",
        "maxPoolSize",
        "fetchSize",
        "minRefreshMinutes"})
@JsonInclude(Include.NON_NULL)
public class JdbcConnectionSettings {

    /// The number of pooled connections used when none is set.
    public static final int DEFAULT_MAX_POOL_SIZE = 5;
    /// The number of rows fetched per round trip when none is set.
    public static final int DEFAULT_FETCH_SIZE = 1_000;

    @JsonProperty
    private final String url;
    @JsonProperty
    private final String credentialName;
    @JsonProperty
    private final Map<String, String> connectionProperties;
    @JsonProperty
    private final Integer maxPoolSize;
    @JsonProperty
    private final Integer fetchSize;
    @JsonProperty
    private final Integer minRefreshMinutes;

    /// Creates JDBC connection settings.
    ///
    /// @param url                  The JDBC URL of the database.
    /// @param credentialName       The name of a Username/Password credential, or `null` for none.
    /// @param connectionProperties Extra properties passed to the driver, or `null` for none.
    /// @param maxPoolSize          The most pooled connections, or `null` for [#DEFAULT_MAX_POOL_SIZE].
    /// @param fetchSize            The rows fetched per round trip, or `null` for [#DEFAULT_FETCH_SIZE].
    /// @param minRefreshMinutes    The shortest automatic rerun interval of a dashboard in minutes, or
    ///                             `null` for no minimum beyond the dashboard's own.
    @JsonCreator
    public JdbcConnectionSettings(@JsonProperty("url") final String url,
                                  @JsonProperty("credentialName") final String credentialName,
                                  @JsonProperty("connectionProperties") final Map<String, String> connectionProperties,
                                  @JsonProperty("maxPoolSize") final Integer maxPoolSize,
                                  @JsonProperty("fetchSize") final Integer fetchSize,
                                  @JsonProperty("minRefreshMinutes") final Integer minRefreshMinutes) {
        this.url = url;
        this.credentialName = credentialName;
        this.connectionProperties = connectionProperties;
        this.maxPoolSize = maxPoolSize;
        this.fetchSize = fetchSize;
        this.minRefreshMinutes = minRefreshMinutes;
    }

    /// @return The JDBC URL of the database, or `null` if not set.
    public String getUrl() {
        return url;
    }

    /// @return The name of the Username/Password credential used to connect, or `null` for none.
    public String getCredentialName() {
        return credentialName;
    }

    /// @return Extra properties passed to the driver, or `null` for none.
    public Map<String, String> getConnectionProperties() {
        return connectionProperties;
    }

    /// @return The most pooled connections, or `null` to use [#DEFAULT_MAX_POOL_SIZE].
    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    /// @return The rows fetched per round trip, or `null` to use [#DEFAULT_FETCH_SIZE].
    public Integer getFetchSize() {
        return fetchSize;
    }

    /// @return The shortest interval, in minutes, at which a dashboard may rerun queries automatically, or `null` for
    /// no minimum beyond the dashboard's own.
    public Integer getMinRefreshMinutes() {
        return minRefreshMinutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JdbcConnectionSettings that = (JdbcConnectionSettings) o;
        return Objects.equals(url, that.url) &&
               Objects.equals(credentialName, that.credentialName) &&
               Objects.equals(connectionProperties, that.connectionProperties) &&
               Objects.equals(maxPoolSize, that.maxPoolSize) &&
               Objects.equals(fetchSize, that.fetchSize) &&
               Objects.equals(minRefreshMinutes, that.minRefreshMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, credentialName, connectionProperties, maxPoolSize, fetchSize, minRefreshMinutes);
    }

    @Override
    public String toString() {
        // Property values may hold sensitive settings, so only their names are shown.
        return "JdbcConnectionSettings{" +
               "url='" + url + '\'' +
               ", credentialName='" + credentialName + '\'' +
               ", connectionPropertyNames=" + (connectionProperties == null
                ? null
                : connectionProperties.keySet()) +
               ", maxPoolSize=" + maxPoolSize +
               ", fetchSize=" + fetchSize +
               ", minRefreshMinutes=" + minRefreshMinutes +
               '}';
    }

    /// @return A builder initialised with the values of these settings.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new, empty builder.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [JdbcConnectionSettings] instances.
    public static final class Builder {

        private String url;
        private String credentialName;
        private Map<String, String> connectionProperties;
        private Integer maxPoolSize;
        private Integer fetchSize;
        private Integer minRefreshMinutes;

        private Builder() {
        }

        private Builder(final JdbcConnectionSettings settings) {
            this.url = settings.url;
            this.credentialName = settings.credentialName;
            this.connectionProperties = settings.connectionProperties;
            this.maxPoolSize = settings.maxPoolSize;
            this.fetchSize = settings.fetchSize;
            this.minRefreshMinutes = settings.minRefreshMinutes;
        }

        /// @param url The JDBC URL of the database.
        /// @return This builder.
        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        /// @param credentialName The name of a Username/Password credential, or `null` for none.
        /// @return This builder.
        public Builder credentialName(final String credentialName) {
            this.credentialName = credentialName;
            return this;
        }

        /// @param connectionProperties Extra properties passed to the driver, or `null` for none.
        /// @return This builder.
        public Builder connectionProperties(final Map<String, String> connectionProperties) {
            this.connectionProperties = connectionProperties;
            return this;
        }

        /// @param maxPoolSize The most pooled connections, or `null` for the default.
        /// @return This builder.
        public Builder maxPoolSize(final Integer maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /// @param fetchSize The rows fetched per round trip, or `null` for the default.
        /// @return This builder.
        public Builder fetchSize(final Integer fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        /// @param minRefreshMinutes The shortest automatic rerun interval of a dashboard in minutes,
        ///                          or `null` for the default.
        /// @return This builder.
        public Builder minRefreshMinutes(final Integer minRefreshMinutes) {
            this.minRefreshMinutes = minRefreshMinutes;
            return this;
        }

        /// @return New [JdbcConnectionSettings] holding the values set on this builder.
        public JdbcConnectionSettings build() {
            return new JdbcConnectionSettings(url,
                    credentialName,
                    connectionProperties,
                    maxPoolSize,
                    fetchSize,
                    minRefreshMinutes);
        }
    }
}
