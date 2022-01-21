package stroom.test.common.util.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceFactoryImpl;
import stroom.db.util.JooqUtil;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
class TestDataSourceFactory implements DataSourceFactory, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceFactoryImpl.class);

    private final Provider<CommonDbConfig> commonDbConfigProvider;

    @Inject
    TestDataSourceFactory(final Provider<CommonDbConfig> commonDbConfigProvider) {
        this.commonDbConfigProvider = commonDbConfigProvider;
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());

        JooqUtil.disableJooqLogoInLogs();
    }

    @Override
    public DataSource create(final AbstractDbConfig dbConfig, final String name, final boolean unique) {
        final AbstractDbConfig mergedConfig = commonDbConfigProvider.get().mergeConfig(dbConfig);
        return DbTestUtil.createTestDataSource(mergedConfig, name, unique);
    }

    @Override
    public void clear() {
        DbTestUtil.clear();
    }
}
