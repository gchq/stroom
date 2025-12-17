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

package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.util.time.StroomDuration;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.zaxxer.hikari.HikariConfig;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HikariUtil {

    private HikariUtil() {
        // Utility class.
    }

    public static HikariConfig createConfig(final AbstractDbConfig dbConfig) {
        return createConfig(dbConfig, null, null, null);
    }

    public static HikariConfig createConfig(final AbstractDbConfig dbConfig,
                                            final String poolName,
                                            final MetricRegistry metricRegistry,
                                            final HealthCheckRegistry healthCheckRegistry) {
        final ConnectionConfig connectionConfig = dbConfig.getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = dbConfig.getConnectionPoolConfig();

        // Validate the connection details.
        DbUtil.validate(connectionConfig);
        // Keep waiting until we can establish a DB connection to allow for the DB to start after the app
        DbUtil.waitForConnection(connectionConfig, poolName);

        return create(
                connectionConfig,
                connectionPoolConfig,
                poolName,
                metricRegistry,
                healthCheckRegistry);
    }

    private static HikariConfig create(final ConnectionConfig connectionConfig,
                                       final ConnectionPoolConfig connectionPoolConfig,
                                       final String poolName,
                                       final MetricRegistry metricRegistry,
                                       final HealthCheckRegistry healthCheckRegistry) {
        final HikariConfig config = new HikariConfig();

        // So we can tell which pool is which in the metrics when >1 db config is specified
        if (poolName != null && !poolName.isBlank()) {
            config.setPoolName(poolName);
        }

        // Attach hikari to our DropWiz metric registry so we can get metrics on the pool
        if (metricRegistry != null) {
            config.setMetricRegistry(metricRegistry);
        }
        if (healthCheckRegistry != null) {
            config.setHealthCheckRegistry(healthCheckRegistry);
        }

        // Pool properties
        copyAndMapProp(connectionPoolConfig::getConnectionTimeout,
                config::setConnectionTimeout,
                StroomDuration::toMillis);
        copyAndMapProp(connectionPoolConfig::getIdleTimeout, config::setIdleTimeout, StroomDuration::toMillis);
        copyAndMapProp(connectionPoolConfig::getMaxLifetime, config::setMaxLifetime, StroomDuration::toMillis);
        copyAndMapProp(connectionPoolConfig::getMinimumIdle, config::setMinimumIdle, Integer::intValue);
        copyAndMapProp(connectionPoolConfig::getMaxPoolSize, config::setMaximumPoolSize, Integer::intValue);
        copyAndMapProp(
                connectionPoolConfig::getLeakDetectionThreshold,
                config::setLeakDetectionThreshold,
                StroomDuration::toMillis);

        copyAndMapProp(connectionConfig::getUrl, config::setJdbcUrl, Function.identity());
        copyAndMapProp(connectionConfig::getUser, config::setUsername, Function.identity());
        copyAndMapProp(connectionConfig::getPassword, config::setPassword, Function.identity());

        // JDBC Driver properties
        copyAndMapProp(connectionPoolConfig::getCachePrepStmts,
                val -> config.addDataSourceProperty("cachePrepStmts", val),
                String::valueOf);
        copyAndMapProp(connectionPoolConfig::getPrepStmtCacheSize,
                val -> config.addDataSourceProperty("prepStmtCacheSize", val),
                String::valueOf);
        copyAndMapProp(connectionPoolConfig::getPrepStmtCacheSqlLimit,
                val -> config.addDataSourceProperty("prepStmtCacheSqlLimit", val),
                String::valueOf);

        return config;
    }

    /**
     * If the source supplier (i.e. getter) has a non-null value then set it
     * on the dest consumer (i.e. setter). Convert the type of the value in the
     * process.
     */
    private static <T1, T2> void copyAndMapProp(final Supplier<T1> source,
                                                final Consumer<T2> dest,
                                                final Function<T1, T2> typeMapper) {
        final T1 sourceValue = source.get();
        if (sourceValue != null) {
            dest.accept(typeMapper.apply(sourceValue));
        }
    }
}
