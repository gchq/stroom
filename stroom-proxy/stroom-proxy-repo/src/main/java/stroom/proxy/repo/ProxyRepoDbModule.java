package stroom.proxy.repo;

import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.db.util.AbstractDataSourceProviderModule;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceProviderProxy;
import stroom.db.util.FlywayUtil;
import stroom.util.db.ForceCoreMigration;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ProxyRepoDbModule extends AbstractModule {

    private static final String MODULE = "stroom-proxy-repo";
    private static final String FLYWAY_LOCATIONS = "stroom/proxy/repo/db/sqlite";
    private static final String FLYWAY_TABLE = "proxy_repo_schema_history";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataSourceProviderModule.class);

    private volatile DataSource dataSource;

    @Override
    protected void configure() {
        super.configure();
    }

    /**
     * We inject {@link ForceCoreMigration} to ensure that the the core DB migration has happened before all
     * other migrations
     */
    @Provides
    @Singleton
    public ProxyRepoDbConnProvider getConnectionProvider(
            final Provider<RepoConfig> configProvider,
            final DataSourceFactory dataSourceFactory) {
        return new DataSourceImpl(() -> {
            if (dataSource == null) {
                synchronized (ProxyRepoDbModule.this) {
                    if (dataSource == null) {
                        LOGGER.debug(() -> "Getting connection provider for " + MODULE);

                        final DbConfig config = getConfig(configProvider);
                        final DataSource ds = dataSourceFactory.create(() -> config);
                        FlywayUtil.migrate(ds, FLYWAY_LOCATIONS, FLYWAY_TABLE, MODULE);
                        this.dataSource = ds;
                    }
                }
            }

            return dataSource;
        });
    }

    private DbConfig getConfig(final Provider<RepoConfig> repoConfigProvider) {
        final DbConfig dbConfig = new DbConfig();
        String dbDir = repoConfigProvider.get().getDbDir();

        Path path;
        if (dbDir == null) {
            throw new RuntimeException("No DB dir has been defined");
        } else {
            path = Paths.get(dbDir);
        }
        if (!Files.isDirectory(path)) {
            throw new RuntimeException("Unable to find DB dir: " + FileUtil.getCanonicalPath(path));
        }

        path = path.resolve("db");
        FileUtil.mkdirs(path);
        path = path.resolve("proxy-repo.db");

        final String fullPath = FileUtil.getCanonicalPath(path);

        final ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setClassName("org.sqlite.JDBC");
        connectionConfig.setUrl("jdbc:sqlite:" + fullPath);
//            connectionConfig.setUser("sa");
//            connectionConfig.setPassword("sa");

        dbConfig.setConnectionConfig(connectionConfig);
        return dbConfig;
    }

    public static class DataSourceImpl extends DataSourceProviderProxy implements ProxyRepoDbConnProvider {

        private DataSourceImpl(final Provider<DataSource> dataSourceProvider) {
            super(dataSourceProvider);
        }
    }
}
