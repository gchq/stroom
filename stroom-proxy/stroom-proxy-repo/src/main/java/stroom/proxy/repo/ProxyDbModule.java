package stroom.proxy.repo;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.AbstractDataSourceProviderModule;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceProxy;
import stroom.db.util.FlywayUtil;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Flushable;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ProxyDbModule extends AbstractModule {

    private static final String MODULE = "stroom-proxy-repo";
    private static final String FLYWAY_LOCATIONS = "stroom/proxy/repo/db/sqlite";
    private static final String FLYWAY_TABLE = "proxy_repo_schema_history";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataSourceProviderModule.class);

    @Override
    protected void configure() {
        super.configure();

        GuiceUtil.buildMultiBinder(binder(), Flushable.class)
                .addBinding(SourceDao.class)
                .addBinding(SourceItemDao.class)
                .addBinding(ForwardAggregateDao.class)
                .addBinding(ForwardSourceDao.class);
    }

    @Provides
    @Singleton
    public ProxyRepoDbConnProvider getConnectionProvider(
            final RepoDbDirProvider repoDbDirProvider,
            final DataSourceFactory dataSourceFactory,
            final ProxyDbConfig proxyDbConfig) {
        LOGGER.debug(() -> "Getting connection provider for " + MODULE);

        final AbstractDbConfig config = getDbConfig(repoDbDirProvider);
        final DataSource dataSource = dataSourceFactory.create(config, MODULE, true);
        FlywayUtil.migrate(dataSource, FLYWAY_LOCATIONS, FLYWAY_TABLE, MODULE);
        return new DataSourceImpl(dataSource, proxyDbConfig);
    }

    private AbstractDbConfig getDbConfig(final RepoDbDirProvider repoDbDirProvider) {
        final Path dbDir = repoDbDirProvider.get();

        FileUtil.mkdirs(dbDir);
        if (!Files.isDirectory(dbDir)) {
            throw new RuntimeException("Unable to find DB dir: " + FileUtil.getCanonicalPath(dbDir));
        }

        final Path path = dbDir.resolve("proxy-repo.db");
        final String fullPath = FileUtil.getCanonicalPath(path);

        final ConnectionConfig connectionConfig = ConnectionConfig.builder()
                .jdbcDriverClassName("org.sqlite.JDBC")
                .url("jdbc:sqlite:" + fullPath)
                .build();

        return new MyProxyRepoDbConfig(connectionConfig);
    }

    private static class MyProxyRepoDbConfig extends AbstractDbConfig {

        public MyProxyRepoDbConfig(final ConnectionConfig connectionConfig) {
            super(connectionConfig, new ConnectionPoolConfig());
        }
    }

    public static class DataSourceImpl extends DataSourceProxy implements ProxyRepoDbConnProvider {

        private final ProxyDbConfig proxyDbConfig;

        private DataSourceImpl(final DataSource dataSource,
                               final ProxyDbConfig proxyDbConfig) {
            super(dataSource, MODULE);
            this.proxyDbConfig = proxyDbConfig;

            for (final String pragma : proxyDbConfig.getGlobalPragma()) {
                try (final Connection connection = super.getConnection()) {
                    pragma(connection, pragma);
                } catch (final SQLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            final Connection connection = super.getConnection();
            for (final String pragma : proxyDbConfig.getConnectionPragma()) {
                pragma(connection, pragma);
            }
            return connection;
        }

        public void pragma(final Connection connection, final String pragma) throws SQLException {
            connection.prepareStatement(pragma).execute();
        }
    }
}
