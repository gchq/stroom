package stroom.gitrepo.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.gitrepo.api.GitRepoConfig.GitRepoDbConfig;

import java.util.List;
import javax.sql.DataSource;

public class GitRepoDbModule extends AbstractFlyWayDbModule<GitRepoDbConfig, GitRepoDbConnProvider> {

    private static final String MODULE = "stroom-gitrepo";

    private static final String FLYWAY_LOCATIONS = "stroom/gitrepo/impl/db/migration";

    private static final String FLYWAY_TABLE = "gitrepo_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
    }

    @Override
    protected Class<GitRepoDbConnProvider> getConnectionProviderType() {
        return GitRepoDbConnProvider.class;
    }

    @Override
    protected GitRepoDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements GitRepoDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
