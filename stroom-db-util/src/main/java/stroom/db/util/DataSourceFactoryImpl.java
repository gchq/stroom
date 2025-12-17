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
import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;

@Singleton
public class DataSourceFactoryImpl implements DataSourceFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final Provider<CommonDbConfig> commonDbConfigProvider;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;
    private static final ConcurrentMap<DataSourceKey, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();

    @Inject
    public DataSourceFactoryImpl(final Provider<CommonDbConfig> commonDbConfigProvider,
                                 final MetricRegistry metricRegistry,
                                 final HealthCheckRegistry healthCheckRegistry) {
        this.commonDbConfigProvider = commonDbConfigProvider;
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());

        JooqUtil.disableJooqLogoInLogs();
    }

    @Override
    public DataSource create(final AbstractDbConfig dbConfig, final String name, final boolean unique) {

        // Create a merged config using the common db config as a base.
        final String className = dbConfig.getClass()
                .getSimpleName();

        final CommonDbConfig commonDbConfig = commonDbConfigProvider.get();
        final AbstractDbConfig mergedConfig = commonDbConfig.mergeConfig(dbConfig);
        final DataSourceKey key = new DataSourceKey(mergedConfig, name, unique);

        LOGGER.debug(() ->
                LogUtil.message("Class: {}\n  {}\n  {}\n  {}",
                        className,
                        dbConfig.getConnectionConfig(),
                        commonDbConfig.getConnectionConfig(),
                        key));

        // Get a data source from a map to limit connections where connection details are common.
        return DATA_SOURCE_MAP.computeIfAbsent(key, k -> {
            final String poolName = k.getPoolName();
            LOGGER.debug(() ->
                    LogUtil.message("Creating datasource for: {}, user: {}, poolName: {}",
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUrl).orElse("null"),
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUser).orElse("null"),
                            poolName));

            final HikariConfig hikariConfig = HikariUtil.createConfig(
                    k.getConfig(),
                    poolName,
                    metricRegistry,
                    healthCheckRegistry);
            return new HikariDataSource(hikariConfig);
        });
    }
}
