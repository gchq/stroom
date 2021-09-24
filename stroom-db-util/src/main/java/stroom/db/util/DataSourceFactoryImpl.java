package stroom.db.util;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class DataSourceFactoryImpl implements DataSourceFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final CommonDbConfig commonDbConfig;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;
    private static final ConcurrentMap<DbConfig, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();
    private static final AtomicBoolean IS_FIRST_POOL = new AtomicBoolean(true);

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
    public DataSource create(final HasDbConfig config) {
        final DbConfig dbConfig = config.getDbConfig();

        // Create a merged config using the common db config as a base.
        final DbConfig mergedConfig = commonDbConfig.mergeConfig(dbConfig);

        LOGGER.debug(() ->
                LogUtil.message("Class: {}\n  {}\n  {}\n  {}",
                        config.getClass().getSimpleName(),
                        dbConfig.getConnectionConfig(),
                        commonDbConfig.getConnectionConfig(),
                        mergedConfig.getConnectionConfig()));

        // Get a data source from a map to limit connections where connection details are common.
        return DATA_SOURCE_MAP.computeIfAbsent(mergedConfig, dbConfigKey -> {
            final String poolName = getPoolName(config);
            LOGGER.debug(() ->
                    LogUtil.message("Creating datasource for: {}, user: {}, poolName: {}",
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUrl).orElse("null"),
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUser).orElse("null"),
                            poolName));

            final HikariConfig hikariConfig = HikariUtil.createConfig(
                    dbConfigKey,
                    poolName,
                    metricRegistry,
                    healthCheckRegistry);
            return new HikariDataSource(hikariConfig);
        });
    }

    private String getPoolName(final HasDbConfig config) {
        // Use the config class name to name our pool.
        // The snag with this is that because we only create a pool when the db config
        // is different from the last one, in most cases we will have one pool and it will be named
        // according to the first db module to hit us, which seems to be LegacyDbConfig.
        return "hikari-" + config.getClass()
                .getSimpleName()
                .replace("Config", "")
                .toLowerCase();
    }
}
