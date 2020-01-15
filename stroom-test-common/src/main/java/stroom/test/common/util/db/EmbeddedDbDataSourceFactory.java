package stroom.test.common.util.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.DbConfig;
import stroom.db.util.DataSourceFactoryImpl;
import stroom.db.util.DbUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class EmbeddedDbDataSourceFactory extends DataSourceFactoryImpl {
    @Inject
    EmbeddedDbDataSourceFactory(final CommonDbConfig commonDbConfig) {
        super(commonDbConfig);
    }

    @Override
    protected DbConfig mergeConfig(final DbConfig dbConfig) {
        final DbConfig mergedConfig = super.mergeConfig(dbConfig);

//        // Set the connection pool values for testing.
//        mergedConfig.getConnectionPoolConfig().setIdleTimeout(1000L);
//        mergedConfig.getConnectionPoolConfig().setMaxLifetime(1000L);
//        mergedConfig.getConnectionPoolConfig().setMaxPoolSize(2);

        if (DbTestUtil.isUseEmbeddedDb()) {
            DbUtil.copyConnectionConfig(DbTestUtil.getOrCreateEmbeddedConnectionConfig(), mergedConfig.getConnectionConfig());
        }

        return mergedConfig;
    }
}
