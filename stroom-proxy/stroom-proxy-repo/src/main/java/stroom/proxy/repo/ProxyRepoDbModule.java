package stroom.proxy.repo;

import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.db.util.AbstractDataSourceProviderModule;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceProxy;
import stroom.db.util.FlywayUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ProxyRepoDbModule extends AbstractModule {

    private static final String MODULE = "stroom-proxy-repo";
    private static final String FLYWAY_LOCATIONS = "stroom/proxy/repo/db/sqlite";
    private static final String FLYWAY_TABLE = "proxy_repo_schema_history";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataSourceProviderModule.class);

    @Override
    protected void configure() {
        super.configure();
    }

    @Provides
    @Singleton
    public ProxyRepoDbConnProvider getConnectionProvider(
            final RepoDbDirProvider repoDbDirProvider,
            final DataSourceFactory dataSourceFactory) {
        LOGGER.debug(() -> "Getting connection provider for " + MODULE);



        final Path dbDir = repoDbDirProvider.get();
        FileUtil.mkdirs(dbDir);
        if (!Files.isDirectory(dbDir)) {
            throw new RuntimeException("Unable to find DB dir: " + FileUtil.getCanonicalPath(dbDir));
        }
        final Path path = dbDir.resolve("proxy-repo.db");
        final String fullPath = FileUtil.getCanonicalPath(path);
        final SQLiteDataSource sqLiteDataSource = new SQLiteDataSource();
        sqLiteDataSource.setUrl("jdbc:sqlite:" + fullPath);
        FlywayUtil.migrate(sqLiteDataSource, FLYWAY_LOCATIONS, FLYWAY_TABLE, MODULE);
        return new DataSourceImpl(sqLiteDataSource);










//        final DbConfig config = getDbConfig(repoDbDirProvider);
//        final DataSource dataSource = dataSourceFactory.create(() -> config, MODULE, true);
//        FlywayUtil.migrate(dataSource, FLYWAY_LOCATIONS, FLYWAY_TABLE, MODULE);
//        return new DataSourceImpl(dataSource);
    }

//    private DbConfig getDbConfig(final RepoDbDirProvider repoDbDirProvider) {
//        final DbConfig dbConfig = new DbConfig();
//        final Path dbDir = repoDbDirProvider.get();
//
//        FileUtil.mkdirs(dbDir);
//        if (!Files.isDirectory(dbDir)) {
//            throw new RuntimeException("Unable to find DB dir: " + FileUtil.getCanonicalPath(dbDir));
//        }
//
//        final Path path = dbDir.resolve("proxy-repo.db");
//        final String fullPath = FileUtil.getCanonicalPath(path);
//
//        final ConnectionConfig connectionConfig = new ConnectionConfig();
//        connectionConfig.setClassName("org.sqlite.JDBC");
//        connectionConfig.setUrl("jdbc:sqlite:" + fullPath);
////            connectionConfig.setUser("sa");
////            connectionConfig.setPassword("sa");
//
//        dbConfig.setConnectionConfig(connectionConfig);
//
//        // SQLite is best used with a single connection.
//        dbConfig.getConnectionPoolConfig().setMaxPoolSize(1);
//
//        return dbConfig;
//    }

    public static class DataSourceImpl extends DataSourceProxy implements ProxyRepoDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
