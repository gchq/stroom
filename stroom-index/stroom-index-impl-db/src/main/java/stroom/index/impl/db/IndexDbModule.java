package stroom.index.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.index.impl.IndexConfig.IndexDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class IndexDbModule extends AbstractFlyWayDbModule<IndexDbConfig, IndexDbConnProvider> {

    private static final String MODULE = "stroom-index";
    private static final String FLYWAY_LOCATIONS = "stroom/index/impl/db/migration";
    private static final String FLYWAY_TABLE = "index_schema_history";

    @Override
    protected void configure() {
        super.configure();


        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(IndexDbConnProvider.class);
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
    protected Class<IndexDbConnProvider> getConnectionProviderType() {
        return IndexDbConnProvider.class;
    }

    @Override
    protected IndexDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements IndexDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
