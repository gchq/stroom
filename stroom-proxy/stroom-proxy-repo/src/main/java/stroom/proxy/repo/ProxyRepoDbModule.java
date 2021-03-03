package stroom.proxy.repo;

import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.util.io.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;

public class ProxyRepoDbModule extends AbstractFlyWayDbModule<ProxyRepoConfig, ProxyRepoDbConnProvider> {

    private static final String MODULE = "stroom-proxy-repo";
    private static final String FLYWAY_LOCATIONS = "stroom/proxy/repo/db/sqlite";
    private static final String FLYWAY_TABLE = "proxy_repo_schema_history";

    @Override
    protected void configure() {
        super.configure();
    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Class<ProxyRepoDbConnProvider> getConnectionProviderType() {
        return ProxyRepoDbConnProvider.class;
    }

    @Override
    protected ProxyRepoConfig modifyConfig(final ProxyRepoConfig config) {
        if (config.getDbConfig() == null) {
            String repoDir = config.getRepoDir();

            Path path;
            if (repoDir == null) {
                throw new RuntimeException("No proxy repository dir has been defined");
            } else {
                path = Paths.get(repoDir);
            }
            if (!Files.isDirectory(path)) {
                throw new RuntimeException("Unable to find repo dir: " + FileUtil.getCanonicalPath(path));
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

            final DbConfig dbConfig = new DbConfig();
            dbConfig.setConnectionConfig(connectionConfig);

            config.setDbConfig(dbConfig);
        }
        return config;
    }

    @Override
    protected ProxyRepoDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    public static class DataSourceImpl extends DataSourceProxy implements ProxyRepoDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
