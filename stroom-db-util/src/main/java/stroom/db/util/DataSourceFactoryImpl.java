package stroom.db.util;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class DataSourceFactoryImpl implements DataSourceFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final CommonDbConfig commonDbConfig;
    private static final Map<DbConfig, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();

    @Inject
    public DataSourceFactoryImpl(final CommonDbConfig commonDbConfig) {
        this.commonDbConfig = commonDbConfig;
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
        return DATA_SOURCE_MAP.computeIfAbsent(mergedConfig, k -> {
            LOGGER.debug(() ->
                    LogUtil.message("Creating datasource for {} with user {}",
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUrl).orElse("null"),
                            Optional.ofNullable(mergedConfig.getConnectionConfig())
                                    .map(ConnectionConfig::getUser).orElse("null")));

            final HikariConfig hikariConfig = HikariUtil.createConfig(k);
            return new HikariDataSource(hikariConfig);
        });
    }
}
