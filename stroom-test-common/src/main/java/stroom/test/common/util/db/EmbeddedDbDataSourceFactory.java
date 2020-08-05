package stroom.test.common.util.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.DataSourceFactoryImpl;
import stroom.db.util.HikariUtil;
import stroom.util.config.FieldMapper;
import stroom.util.shared.Clearable;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Singleton
class EmbeddedDbDataSourceFactory extends DataSourceFactoryImpl implements Clearable {
    private static final ThreadLocal<DataSource> DATA_SOURCE_THREAD_LOCAL = new ThreadLocal<>();
    private DataSource dataSource;

    @Inject
    EmbeddedDbDataSourceFactory(final CommonDbConfig commonDbConfig) {
        super(commonDbConfig);
    }

    @Override
    public DataSource create(final HasDbConfig config) {
        // See if we have a local data source.
        DataSource dataSource = getDataSource();
        if (dataSource == null) {
            if (!DbTestUtil.isUseEmbeddedDb()) {
                dataSource = super.create(config);
            } else {
                final DbConfig dbConfig = config.getDbConfig();

                // Create a merged config using the common db config as a base.
                final DbConfig mergedConfig = mergeConfig(dbConfig);

                FieldMapper.copyNonDefaults(
                        DbTestUtil.getOrCreateEmbeddedConnectionConfig(),
                        mergedConfig.getConnectionConfig(),
                        new ConnectionConfig());

                final HikariConfig hikariConfig = HikariUtil.createConfig(mergedConfig);
                dataSource = new HikariDataSource(hikariConfig);
            }
            setDataSource(dataSource);
        }
        return dataSource;
    }

    @Override
    public void clear() {
        final DataSource dataSource = getDataSource();
        if (dataSource != null) {
            // Clear the database.
            try (final Connection connection = dataSource.getConnection()) {
                DbTestUtil.clearAllTables(connection);
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private DataSource getDataSource() {
        // If we don't have a local datasource then see if there is a cached one.
        if (this.dataSource == null) {
            this.dataSource = DATA_SOURCE_THREAD_LOCAL.get();
        }
        return this.dataSource;
    }

    private void setDataSource(final DataSource ds) {
        this.dataSource = ds;
        DATA_SOURCE_THREAD_LOCAL.set(ds);
    }
}
