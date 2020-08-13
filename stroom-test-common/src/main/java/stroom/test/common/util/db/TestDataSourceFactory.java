package stroom.test.common.util.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceFactoryImpl;
import stroom.db.util.JooqUtil;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
class TestDataSourceFactory implements DataSourceFactory, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final CommonDbConfig commonDbConfig;

    @Inject
    TestDataSourceFactory(final CommonDbConfig commonDbConfig) {
        this.commonDbConfig = commonDbConfig;
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());

        JooqUtil.disableJooqLogoInLogs();
    }

    @Override
    public DataSource create(final HasDbConfig config) {
        final DbConfig dbConfig = config.getDbConfig();
        final DbConfig mergedConfig = commonDbConfig.mergeConfig(dbConfig);
        return DbTestUtil.createTestDataSource(mergedConfig);
    }

    @Override
    public void clear() {
        DbTestUtil.clear();
    }
}
