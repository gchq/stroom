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

package stroom.config.common;

import stroom.util.config.PropertyUtil;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class ConnectionPoolConfig extends AbstractConfig implements IsStroomConfig {

    public static final String COMMON_CONN_POOL_DESC = "See " +
            "https://github.com/brettwooldridge/HikariCP for further " +
            "details on configuring the connection pool.";

    public static final String COMMON_JDBC_DESC = "See " +
            "https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration for further " +
            "details on configuring the MySQL JDBC driver properties.";

    private static final ConnectionPoolConfig DEFAULTS = new ConnectionPoolConfig();

    // JDBC driver level props
    private final boolean cachePrepStmts;
    // TODO 30/11/2021 AT: Make final
    private int prepStmtCacheSize;
    private final int prepStmtCacheSqlLimit;

    // Hikari pool props
    private final StroomDuration connectionTimeout;
    private final StroomDuration idleTimeout;
    private final StroomDuration maxLifetime;
    private final StroomDuration leakDetectionThreshold;
    private final int minimumIdle;
    private final int maxPoolSize;

    public ConnectionPoolConfig() {
        // JDBC driver level props
        cachePrepStmts = false;
        prepStmtCacheSize = 25;
        prepStmtCacheSqlLimit = 256;

        // Hikari pool props
        connectionTimeout = StroomDuration.ofSeconds(30);
        idleTimeout = StroomDuration.ofMinutes(10);
        maxLifetime = StroomDuration.ofMinutes(30);
        leakDetectionThreshold = StroomDuration.ZERO;
        minimumIdle = 10;
        maxPoolSize = 30;
    }

    @JsonCreator
    public ConnectionPoolConfig(@JsonProperty("cachePrepStmts") final boolean cachePrepStmts,
                                @JsonProperty("prepStmtCacheSize") final int prepStmtCacheSize,
                                @JsonProperty("prepStmtCacheSqlLimit") final int prepStmtCacheSqlLimit,
                                @JsonProperty("connectionTimeout") final StroomDuration connectionTimeout,
                                @JsonProperty("idleTimeout") final StroomDuration idleTimeout,
                                @JsonProperty("maxLifetime") final StroomDuration maxLifetime,
                                @JsonProperty("leakDetectionThreshold") final StroomDuration leakDetectionThreshold,
                                @JsonProperty("minimumIdle") final int minimumIdle,
                                @JsonProperty("maxPoolSize") final int maxPoolSize) {
        this.cachePrepStmts = cachePrepStmts;
        this.prepStmtCacheSize = prepStmtCacheSize;
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
        this.connectionTimeout = connectionTimeout;
        this.idleTimeout = idleTimeout;
        this.maxLifetime = maxLifetime;
        this.leakDetectionThreshold = leakDetectionThreshold;
        this.minimumIdle = minimumIdle;
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * Merges the non-null values of other into this. If other is null return this.
     */
    public ConnectionPoolConfig merge(final ConnectionPoolConfig other,
                                      final boolean copyNulls,
                                      final boolean copyDefaults) {

        if (other == null) {
            return this;
        } else {
            return new ConnectionPoolConfig(
                    PropertyUtil.mergeValues(
                            other.cachePrepStmts,
                            cachePrepStmts,
                            DEFAULTS.cachePrepStmts,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.prepStmtCacheSize,
                            prepStmtCacheSize,
                            DEFAULTS.prepStmtCacheSize,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.prepStmtCacheSqlLimit,
                            prepStmtCacheSqlLimit,
                            DEFAULTS.prepStmtCacheSqlLimit,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.connectionTimeout,
                            connectionTimeout,
                            DEFAULTS.connectionTimeout,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.idleTimeout,
                            idleTimeout,
                            DEFAULTS.idleTimeout,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.maxLifetime,
                            maxLifetime,
                            DEFAULTS.maxLifetime,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.leakDetectionThreshold,
                            leakDetectionThreshold,
                            DEFAULTS.leakDetectionThreshold,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.minimumIdle,
                            minimumIdle,
                            DEFAULTS.minimumIdle,
                            copyNulls,
                            copyDefaults),
                    PropertyUtil.mergeValues(
                            other.maxPoolSize,
                            maxPoolSize,
                            DEFAULTS.maxPoolSize,
                            copyNulls,
                            copyDefaults));
        }
    }


    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "Sets the cachePrepStmts property on the at the JDBC driver level. Set to true to " +
                    "enable caching of prepared statements at the JDBC driver level. " +
                    COMMON_JDBC_DESC)
    public boolean getCachePrepStmts() {
        return cachePrepStmts;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "Sets the prepStmtCacheSize property on the at the JDBC driver level. " +
                    "The number of prepared statements that the driver will cache per connection. " +
                    COMMON_JDBC_DESC)
    @Min(0)
    public int getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    @Deprecated(forRemoval = true)
    public void setPrepStmtCacheSize(final int prepStmtCacheSize) {
        this.prepStmtCacheSize = prepStmtCacheSize;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "Sets the prepStmtCacheSqlLimit property on the at the JDBC driver level. The " +
                    "maximum length for a prepared SQL statement that can be cached. " +
                    COMMON_JDBC_DESC)
    @Min(0)
    public int getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum amount of time that a client will wait for a connection from the pool. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum amount of time that a connection can sit idle in the pool. " +
                    "Only applies when minimumIdle is defined to be less than maximumPoolSize. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getIdleTimeout() {
        return idleTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum lifetime of a connection in the pool. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getMaxLifetime() {
        return maxLifetime;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The minimum number of idle connections that Hikari tries to maintain in the pool. " +
                    COMMON_CONN_POOL_DESC)
    @Min(0)
    public int getMinimumIdle() {
        return minimumIdle;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum size that the pool is allowed to reach, including both idle and in-use connections. " +
                    COMMON_CONN_POOL_DESC)
    @Min(0)
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The amount of time that a connection can be out of the pool before a message is logged indicating " +
                    "a possible connection leak. A value of 0 means leak detection is disabled. Lowest acceptable " +
                    "value for enabling leak detection is 2 seconds." +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getLeakDetectionThreshold() {
        return leakDetectionThreshold;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionPoolConfig that = (ConnectionPoolConfig) o;
        return cachePrepStmts == that.cachePrepStmts
                && prepStmtCacheSize == that.prepStmtCacheSize
                && prepStmtCacheSqlLimit == that.prepStmtCacheSqlLimit
                && minimumIdle == that.minimumIdle
                && maxPoolSize == that.maxPoolSize
                && Objects.equals(connectionTimeout, that.connectionTimeout)
                && Objects.equals(idleTimeout, that.idleTimeout)
                && Objects.equals(maxLifetime, that.maxLifetime)
                && Objects.equals(leakDetectionThreshold, that.leakDetectionThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cachePrepStmts,
                prepStmtCacheSize,
                prepStmtCacheSqlLimit,
                connectionTimeout,
                idleTimeout,
                maxLifetime,
                leakDetectionThreshold,
                minimumIdle,
                maxPoolSize);
    }

    @Override
    public String toString() {
        return "ConnectionPoolConfig{" +
                "cachePrepStmts=" + cachePrepStmts +
                ", prepStmtCacheSize=" + prepStmtCacheSize +
                ", prepStmtCacheSqlLimit=" + prepStmtCacheSqlLimit +
                ", connectionTimeout=" + connectionTimeout +
                ", idleTimeout=" + idleTimeout +
                ", maxLifetime=" + maxLifetime +
                ", leakDetectionThreshold=" + leakDetectionThreshold +
                ", minimumIdle=" + minimumIdle +
                ", maxPoolSize=" + maxPoolSize +
                '}';
    }
}
