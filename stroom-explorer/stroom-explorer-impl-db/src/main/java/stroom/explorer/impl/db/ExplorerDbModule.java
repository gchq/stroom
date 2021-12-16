package stroom.explorer.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.explorer.impl.ExplorerConfig.ExplorerDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class ExplorerDbModule extends AbstractFlyWayDbModule<ExplorerDbConfig, ExplorerDbConnProvider> {

    private static final String MODULE = "stroom-explorer";
    private static final String FLYWAY_LOCATIONS = "stroom/explorer/impl/db/migration";
    private static final String FLYWAY_TABLE = "explorer_schema_history";

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(ExplorerDbConnProvider.class);
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
    protected Class<ExplorerDbConnProvider> getConnectionProviderType() {
        return ExplorerDbConnProvider.class;
    }

    @Override
    protected ExplorerDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements ExplorerDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
