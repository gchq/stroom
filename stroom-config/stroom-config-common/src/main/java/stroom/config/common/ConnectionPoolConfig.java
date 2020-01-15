/*
 * Copyright 2018 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import java.util.Objects;

public class ConnectionPoolConfig extends AbstractConfig {

    public static final String COMMON_CONN_POOL_DESC = "See " +
        "https://github.com/brettwooldridge/HikariCP for further " +
        "details on configuring the connection pool.";

    public static final String COMMON_JDBC_DESC = "See " +
        "https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration for further " +
        "details on configuring the MySQL JDBC driver properties.";

    // JDBC driver level props
    private Boolean cachePrepStmts;
    private Integer prepStmtCacheSize;
    private Integer prepStmtCacheSqlLimit;

    // Hikari pool props
    private Long connectionTimeout;
    private Long idleTimeout;
    private Long maxLifetime;
    private Integer minimumIdle;
    private Integer maxPoolSize;

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "Sets the cachePrepStmts property on the at the JDBC driver level. Set to true to " +
        "enable caching of prepared statements at the JDBC driver level. Disabled by default." +
        COMMON_JDBC_DESC)
    public Boolean getCachePrepStmts() {
        return cachePrepStmts;
    }

    @SuppressWarnings("unused")
    public void setCachePrepStmts(final Boolean cachePrepStmts) {
        this.cachePrepStmts = cachePrepStmts;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "Sets the prepStmtCacheSize property on the at the JDBC driver level. " +
        "The number of prepared statements that the driver will cache per connection. Defaults to 25. "
        + COMMON_JDBC_DESC)
    public Integer getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    public void setPrepStmtCacheSize(final Integer prepStmtCacheSize) {
        this.prepStmtCacheSize = prepStmtCacheSize;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "Sets the prepStmtCacheSqlLimit property on the at the JDBC driver level. The " +
        "maximum length for a prepared SQL statement that can be cached. Defaults to 256. " +
        COMMON_JDBC_DESC)
    public Integer getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    @SuppressWarnings("unused")
    public void setPrepStmtCacheSqlLimit(final Integer prepStmtCacheSqlLimit) {
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "the maximum number of milliseconds that a client will wait for a connection from the pool. " +
        "Defaults to 30000 (30s). " +
        COMMON_CONN_POOL_DESC)
    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    @SuppressWarnings("unused")
    public void setConnectionTimeout(final Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "The maximum amount of time in milliseconds that a connection can sit idle in the pool. " +
        "Only applies when minimumIdle is defined to be less than maximumPoolSize. " +
        "Defaults to 600000 (10mins). " +
        COMMON_CONN_POOL_DESC)
    public Long getIdleTimeout() {
        return idleTimeout;
    }

    @SuppressWarnings("unused")
    public void setIdleTimeout(final Long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "The maximum lifetime (in milliseconds) of a connection in the pool. " +
        "Defaults to 1800000 (30mins). " +
        COMMON_CONN_POOL_DESC)
    public Long getMaxLifetime() {
        return maxLifetime;
    }

    @SuppressWarnings("unused")
    public void setMaxLifetime(final Long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "The minimum number of idle connections that Hikari tries to maintain in the pool. " +
        "Defaults to 10 (same as maxPoolSize). " +
        COMMON_CONN_POOL_DESC)
    public Integer getMinimumIdle() {
        return minimumIdle;
    }

    @SuppressWarnings("unused")
    public void setMinimumIdle(final Integer minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription(
        "The maximum size that the pool is allowed to reach, including both idle " +
        "and in-use connections. Defaults to 10. " +
        COMMON_CONN_POOL_DESC)
    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    @SuppressWarnings("unused")
    public void setMaxPoolSize(final Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

//    @Override
//    public boolean equals(final Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        final ConnectionPoolConfig that = (ConnectionPoolConfig) o;
//        return cachePrepStmts == that.cachePrepStmts &&
//                prepStmtCacheSize == that.prepStmtCacheSize &&
//                prepStmtCacheSqlLimit == that.prepStmtCacheSqlLimit;
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(cachePrepStmts, prepStmtCacheSize, prepStmtCacheSqlLimit);
//    }
//
//    @Override
//    public String toString() {
//        return "ConnectionPoolConfig{" +
//                "cachePrepStmts=" + cachePrepStmts +
//                ", prepStmtCacheSize=" + prepStmtCacheSize +
//                ", prepStmtCacheSqlLimit=" + prepStmtCacheSqlLimit +
//                '}';
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConnectionPoolConfig that = (ConnectionPoolConfig) o;
        return cachePrepStmts == that.cachePrepStmts &&
                prepStmtCacheSize == that.prepStmtCacheSize &&
                prepStmtCacheSqlLimit == that.prepStmtCacheSqlLimit &&
                Objects.equals(idleTimeout, that.idleTimeout) &&
                Objects.equals(maxLifetime, that.maxLifetime) &&
                Objects.equals(maxPoolSize, that.maxPoolSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cachePrepStmts, prepStmtCacheSize, prepStmtCacheSqlLimit, idleTimeout, maxLifetime, maxPoolSize);
    }
}
