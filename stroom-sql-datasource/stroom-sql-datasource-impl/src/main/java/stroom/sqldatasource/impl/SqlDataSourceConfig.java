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

package stroom.sqldatasource.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/// Server configuration for SQL Data Source documents.
///
/// It holds the controls an administrator keeps over what the documents may connect to, and the
/// ceilings on the limits a document may set.
@JsonPropertyOrder(alphabetic = true)
public class SqlDataSourceConfig extends AbstractConfig implements IsStroomConfig {

    static final int DEFAULT_MAX_ROWS_LIMIT = 1_000_000;
    static final int DEFAULT_MAX_POOL_SIZE_LIMIT = 20;
    static final StroomDuration DEFAULT_MAX_QUERY_TIMEOUT = StroomDuration.ofMinutes(10);

    // Driver properties that can read local files, load classes, run code or turn off read-only
    // connections. Matched without regard to case. The MySQL and PostgreSQL names are those of the
    // drivers Stroom ships.
    static final List<String> DEFAULT_DENIED_CONNECTION_PROPERTIES = List.of(
            // MySQL: local files.
            "allowLoadLocalInfile",
            "allowLoadLocalInfileInPath",
            "allowUrlInLocalInfile",
            "clientCertificateKeyStoreUrl",
            "ociConfigFile",
            "serverRSAPublicKeyFile",
            "trustCertificateKeyStoreUrl",
            // MySQL: classes loaded by name.
            "authenticationPlugins",
            "autoDeserialize",
            "clientInfoProvider",
            "connectionLifecycleInterceptors",
            "defaultAuthenticationPlugin",
            "exceptionInterceptors",
            "ha.loadBalanceStrategy",
            "loadBalanceExceptionChecker",
            "logger",
            "profilerEventHandler",
            "propertiesTransform",
            "queryInterceptors",
            "socketFactory",
            // MySQL: read-only.
            "readOnlyPropagatesToServer",
            // PostgreSQL: local files and services.
            "gsslib",
            "jaasLogin",
            "loggerFile",
            "options",
            "service",
            "sslcert",
            "sslkey",
            "sslrootcert",
            // PostgreSQL: classes loaded by name.
            "authenticationPluginClassName",
            "socketFactoryArg",
            "sslfactory",
            "sslfactoryarg",
            "sslhostnameverifier",
            "sslpasswordcallback",
            "sspiServiceClass",
            // PostgreSQL: read-only.
            "readOnly",
            "readOnlyMode");

    private final List<String> allowedUrlPatterns;
    private final List<String> deniedConnectionProperties;
    private final String driverDirectory;
    private final StroomDuration maxQueryTimeout;
    private final int maxRowsLimit;
    private final int maxPoolSizeLimit;
    private final CacheConfig connectionPoolCache;
    private final CacheConfig fieldCache;
    private final List<String> allowedAthenaWorkGroups;
    private final List<String> allowedAthenaOutputLocations;
    private final boolean allowDefaultAwsCredentials;

    /// Creates the configuration with its default values.
    public SqlDataSourceConfig() {
        allowedUrlPatterns = List.of();
        deniedConnectionProperties = DEFAULT_DENIED_CONNECTION_PROPERTIES;
        driverDirectory = null;
        maxQueryTimeout = DEFAULT_MAX_QUERY_TIMEOUT;
        maxRowsLimit = DEFAULT_MAX_ROWS_LIMIT;
        maxPoolSizeLimit = DEFAULT_MAX_POOL_SIZE_LIMIT;
        connectionPoolCache = createDefaultConnectionPoolCache();
        fieldCache = createDefaultFieldCache();
        allowedAthenaWorkGroups = List.of();
        allowedAthenaOutputLocations = List.of();
        allowDefaultAwsCredentials = false;
    }

    /// Creates the configuration from JSON or YAML. Any value not supplied takes its default.
    ///
    /// @param allowedUrlPatterns         Regular expressions a JDBC URL must match one of.
    /// @param deniedConnectionProperties Driver properties a document may not set.
    /// @param driverDirectory            A directory of extra JDBC driver jars, or `null` for none.
    /// @param maxQueryTimeout            The longest timeout a document may set.
    /// @param maxRowsLimit               The highest row limit a document may set.
    /// @param maxPoolSizeLimit           The largest connection pool a document may set.
    /// @param connectionPoolCache        The cache of connection pools, one per document.
    /// @param fieldCache                 The cache of the columns each SQL query returns.
    /// @param allowedAthenaWorkGroups    Regular expressions an Athena `region/workgroup` must match one of.
    /// @param allowedAthenaOutputLocations Regular expressions an Athena output location must match one of.
    /// @param allowDefaultAwsCredentials True to let Athena documents with no credential use Stroom's own
    ///                                   AWS identity.
    @SuppressWarnings("unused")
    @JsonCreator
    public SqlDataSourceConfig(
            @JsonProperty("allowedUrlPatterns") final List<String> allowedUrlPatterns,
            @JsonProperty("deniedConnectionProperties") final List<String> deniedConnectionProperties,
            @JsonProperty("driverDirectory") final String driverDirectory,
            @JsonProperty("maxQueryTimeout") final StroomDuration maxQueryTimeout,
            @JsonProperty("maxRowsLimit") final Integer maxRowsLimit,
            @JsonProperty("maxPoolSizeLimit") final Integer maxPoolSizeLimit,
            @JsonProperty("connectionPoolCache") final CacheConfig connectionPoolCache,
            @JsonProperty("fieldCache") final CacheConfig fieldCache,
            @JsonProperty("allowedAthenaWorkGroups") final List<String> allowedAthenaWorkGroups,
            @JsonProperty("allowedAthenaOutputLocations") final List<String> allowedAthenaOutputLocations,
            @JsonProperty("allowDefaultAwsCredentials") final Boolean allowDefaultAwsCredentials) {
        this.allowedUrlPatterns = NullSafe.list(allowedUrlPatterns);
        this.deniedConnectionProperties = Objects.requireNonNullElse(
                deniedConnectionProperties,
                DEFAULT_DENIED_CONNECTION_PROPERTIES);
        this.driverDirectory = driverDirectory;
        this.maxQueryTimeout = Objects.requireNonNullElse(maxQueryTimeout, DEFAULT_MAX_QUERY_TIMEOUT);
        this.maxRowsLimit = Objects.requireNonNullElse(maxRowsLimit, DEFAULT_MAX_ROWS_LIMIT);
        this.maxPoolSizeLimit = Objects.requireNonNullElse(maxPoolSizeLimit, DEFAULT_MAX_POOL_SIZE_LIMIT);
        this.connectionPoolCache = Objects.requireNonNullElseGet(
                connectionPoolCache,
                SqlDataSourceConfig::createDefaultConnectionPoolCache);
        this.fieldCache = Objects.requireNonNullElseGet(
                fieldCache,
                SqlDataSourceConfig::createDefaultFieldCache);
        this.allowedAthenaWorkGroups = NullSafe.list(allowedAthenaWorkGroups);
        this.allowedAthenaOutputLocations = NullSafe.list(allowedAthenaOutputLocations);
        this.allowDefaultAwsCredentials = Objects.requireNonNullElse(allowDefaultAwsCredentials, false);
    }

    /// @return The regular expressions an Athena `region/workgroup` must match one of.
    @NotNull
    @JsonPropertyDescription("Regular expressions that the AWS region and Athena workgroup of an Amazon Athena " +
            "SQL Data Source, written as 'region/workgroup', must match one of, e.g. '^eu-west-2/stroom-.*$'. " +
            "Checked when the document is saved and again when it runs a query. With no patterns, no Athena " +
            "connection is allowed. Each workgroup should set a per-query data scan limit, as that is the " +
            "control on cost. The AWS identities used, and the accounts of any federated connectors, should " +
            "only be able to read, as Stroom cannot see what a connector does with the SQL it is passed.")
    @JsonProperty("allowedAthenaWorkGroups")
    public List<String> getAllowedAthenaWorkGroups() {
        return allowedAthenaWorkGroups;
    }

    /// @return The regular expressions an Athena output location must match one of.
    @NotNull
    @JsonPropertyDescription("Regular expressions that the S3 output location of an Amazon Athena SQL Data " +
            "Source must match one of, e.g. '^s3://stroom-athena-results/.*$'. Every query writes its full " +
            "result there, with the AWS identity of the user running it. With no patterns, documents may not " +
            "set an output location, and the workgroup's own is used.")
    @JsonProperty("allowedAthenaOutputLocations")
    public List<String> getAllowedAthenaOutputLocations() {
        return allowedAthenaOutputLocations;
    }

    /// @return True if Athena documents with no credential may use Stroom's own AWS identity.
    @JsonPropertyDescription("Whether an Amazon Athena SQL Data Source that names no credential may use " +
            "Stroom's own AWS identity from the default credentials chain: its instance or container role, " +
            "web identity, environment or profile. Any user who can create a document could then query " +
            "whatever that identity can read, so it is off by default and documents must name an AWS access " +
            "key credential, which needs Use permission.")
    @JsonProperty("allowDefaultAwsCredentials")
    public boolean isAllowDefaultAwsCredentials() {
        return allowDefaultAwsCredentials;
    }

    /// @return The regular expressions a JDBC URL must match one of.
    @NotNull
    @JsonPropertyDescription("Regular expressions that the JDBC URL of a SQL Data Source must match one of, " +
            "e.g. '^jdbc:mysql://reporting-db\\.example\\.com:3306/.*$'. A URL is checked when the document " +
            "is saved and again when it connects. With no patterns, no JDBC connection is allowed. " +
            "Amazon Athena connections are controlled by allowedAthenaWorkGroups instead.")
    @JsonProperty("allowedUrlPatterns")
    public List<String> getAllowedUrlPatterns() {
        return allowedUrlPatterns;
    }

    /// @return The driver properties a document may not set, matched without regard to case.
    @NotNull
    @JsonPropertyDescription("JDBC driver properties that a SQL Data Source may not set, either as a " +
            "connection property or in its URL, because they can read local files, load classes or run code. " +
            "Names are matched without regard to case.")
    @JsonProperty("deniedConnectionProperties")
    public List<String> getDeniedConnectionProperties() {
        return deniedConnectionProperties;
    }

    /// @return A directory of extra JDBC driver jars, or `null` for none.
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("A directory holding extra JDBC driver jars, for databases other than the " +
            "MySQL and PostgreSQL drivers Stroom ships. If not set, no extra drivers are loaded.")
    @JsonProperty("driverDirectory")
    public String getDriverDirectory() {
        return driverDirectory;
    }

    /// @return The longest query timeout a document may set.
    @NotNull
    @JsonPropertyDescription("The longest query timeout a SQL Data Source may set. A longer timeout " +
            "in a document is reduced to this.")
    @JsonProperty("maxQueryTimeout")
    public StroomDuration getMaxQueryTimeout() {
        return maxQueryTimeout;
    }

    /// @return The highest row limit a document may set.
    @Min(1)
    @JsonPropertyDescription("The highest row limit a SQL Data Source may set. A higher limit in a document " +
            "is reduced to this.")
    @JsonProperty("maxRowsLimit")
    public int getMaxRowsLimit() {
        return maxRowsLimit;
    }

    /// @return The largest connection pool a document may set.
    @Min(1)
    @JsonPropertyDescription("The largest connection pool a JDBC SQL Data Source may set. A larger pool in a " +
            "document is reduced to this.")
    @JsonProperty("maxPoolSizeLimit")
    public int getMaxPoolSizeLimit() {
        return maxPoolSizeLimit;
    }

    /// @return The cache of connection pools, one per document.
    @NotNull
    @JsonPropertyDescription("The cache of connection pools, one per JDBC SQL Data Source. A pool that has " +
            "not been used for the expiry time is closed.")
    @JsonProperty("connectionPoolCache")
    public CacheConfig getConnectionPoolCache() {
        return connectionPoolCache;
    }

    /// @return The cache of the columns each SQL query returns.
    @NotNull
    @JsonPropertyDescription("The cache of the columns returned by the SQL of each SQL Data Source query, " +
            "keyed by document and SQL. It lets the query editors list a query's fields without asking the " +
            "database each time.")
    @JsonProperty("fieldCache")
    public CacheConfig getFieldCache() {
        return fieldCache;
    }

    @Override
    public String toString() {
        return "SqlDataSourceConfig{" +
               "allowedUrlPatterns=" + allowedUrlPatterns +
               ", deniedConnectionProperties=" + deniedConnectionProperties +
               ", driverDirectory='" + driverDirectory + '\'' +
               ", maxQueryTimeout=" + maxQueryTimeout +
               ", maxRowsLimit=" + maxRowsLimit +
               ", maxPoolSizeLimit=" + maxPoolSizeLimit +
               ", connectionPoolCache=" + connectionPoolCache +
               ", fieldCache=" + fieldCache +
               ", allowedAthenaWorkGroups=" + allowedAthenaWorkGroups +
               ", allowedAthenaOutputLocations=" + allowedAthenaOutputLocations +
               ", allowDefaultAwsCredentials=" + allowDefaultAwsCredentials +
               '}';
    }

    private static CacheConfig createDefaultConnectionPoolCache() {
        return CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    private static CacheConfig createDefaultFieldCache() {
        return CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }
}
