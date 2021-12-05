/*
 * Copyright 2018 Crown Copyright
 *
 *
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See the License for the specific language governing permissions and
 * Unless required by applicable law or agreed to in writing, software
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * You may obtain a copy of the License at
 * distributed under the License is distributed on an "AS IS" BASIS,
 * import stroom.util.shared.IsStroomConfig;
 * limitations under the License.
 * you may not use this file except in compliance with the License.
 */

package stroom.config.common;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.validation.constraints.Min;

@NotInjectableConfig
public class ConnectionPoolConfig extends AbstractConfig implements IsStroomConfig {

    public static final String COMMON_CONN_POOL_DESC = "See " +
            "https://github.com/brettwooldridge/HikariCP for further " +
            "details on configuring the connection pool.";

    public static final String COMMON_JDBC_DESC = "See " +
            "https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration for further " +
            "details on configuring the MySQL JDBC driver properties.";

    // JDBC driver level props
    private boolean cachePrepStmts = false;
    private int prepStmtCacheSize = 25;
    private int prepStmtCacheSqlLimit = 256;

    // Hikari pool props
    private StroomDuration connectionTimeout = StroomDuration.ofSeconds(30);
    private StroomDuration idleTimeout = StroomDuration.ofMinutes(10);
    private StroomDuration maxLifetime = StroomDuration.ofMinutes(30);
    private StroomDuration leakDetectionThreshold = StroomDuration.ZERO;
    private int minimumIdle = 10;
    private int maxPoolSize = 30;

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "Sets the cachePrepStmts property on the at the JDBC driver level. Set to true to " +
                    "enable caching of prepared statements at the JDBC driver level. " +
                    COMMON_JDBC_DESC)
    public boolean getCachePrepStmts() {
        return cachePrepStmts;
    }

    @SuppressWarnings("unused")
    public void setCachePrepStmts(final boolean cachePrepStmts) {
        this.cachePrepStmts = cachePrepStmts;
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

    @SuppressWarnings("unused")
    public void setPrepStmtCacheSqlLimit(final int prepStmtCacheSqlLimit) {
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum amount of time that a client will wait for a connection from the pool. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    @SuppressWarnings("unused")
    public void setConnectionTimeout(final StroomDuration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum amount of time that a connection can sit idle in the pool. " +
                    "Only applies when minimumIdle is defined to be less than maximumPoolSize. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getIdleTimeout() {
        return idleTimeout;
    }

    @SuppressWarnings("unused")
    public void setIdleTimeout(final StroomDuration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum lifetime of a connection in the pool. " +
                    COMMON_CONN_POOL_DESC)
    public StroomDuration getMaxLifetime() {
        return maxLifetime;
    }

    @SuppressWarnings("unused")
    public void setMaxLifetime(final StroomDuration maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The minimum number of idle connections that Hikari tries to maintain in the pool. " +
                    COMMON_CONN_POOL_DESC)
    @Min(0)
    public int getMinimumIdle() {
        return minimumIdle;
    }

    @SuppressWarnings("unused")
    public void setMinimumIdle(final int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The maximum size that the pool is allowed to reach, including both idle and in-use connections. " +
                    COMMON_CONN_POOL_DESC)
    @Min(0)
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @SuppressWarnings("unused")
    public void setMaxPoolSize(final int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
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

    @SuppressWarnings("unused")
    public void setLeakDetectionThreshold(final StroomDuration leakDetectionThreshold) {
        this.leakDetectionThreshold = leakDetectionThreshold;
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
