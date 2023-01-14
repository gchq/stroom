package stroom.meta.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.meta.impl.MetaServiceConfig.MetaServiceDbConfig;

import javax.sql.DataSource;

public class MetaDbModule extends AbstractFlyWayDbModule<MetaServiceDbConfig, MetaDbConnProvider> {

    private static final String MODULE = "stroom-meta";
    private static final String FLYWAY_LOCATIONS = "stroom/meta/impl/db/migration";
    private static final String FLYWAY_TABLE = "meta_schema_history";

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
    protected Class<MetaDbConnProvider> getConnectionProviderType() {
        return MetaDbConnProvider.class;
    }

    @Override
    protected MetaDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements MetaDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
