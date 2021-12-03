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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class DataSourceFactoryImpl implements DataSourceFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final CommonDbConfig commonDbConfig;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;
    private static final ConcurrentMap<DataSourceKey, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();

    @Inject
    public DataSourceFactoryImpl(final CommonDbConfig commonDbConfig,
                                 final MetricRegistry metricRegistry,
                                 final HealthCheckRegistry healthCheckRegistry) {
        this.commonDbConfig = commonDbConfig;
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
