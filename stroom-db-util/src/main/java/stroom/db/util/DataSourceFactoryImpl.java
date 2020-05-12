package stroom.db.util;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DataSourceFactoryImpl implements DataSourceFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final CommonDbConfig commonDbConfig;
    private static final Map<DbConfig, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();

    @Inject
    public DataSourceFactoryImpl(final CommonDbConfig commonDbConfig) {
        this.commonDbConfig = commonDbConfig;
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());

        JooqUtil.disableJooqLogoInLogs();
    }

    public DataSource create(final HasDbConfig config) {
        final DbConfig dbConfig = config.getDbConfig();

        // Create a merged config using the common db config as a base.
        final DbConfig mergedConfig = mergeConfig(dbConfig);

        // Get a data source from a map to limit connections where connection details are common.
        return DATA_SOURCE_MAP.computeIfAbsent(mergedConfig, k -> {
            final HikariConfig hikariConfig = HikariUtil.createConfig(k);
            return new HikariDataSource(hikariConfig);
        });
    }

    protected DbConfig mergeConfig(final DbConfig dbConfig) {
        return commonDbConfig.mergeConfig(dbConfig);
    }
}
