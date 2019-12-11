package stroom.storedquery.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.sql.DataSource;

public class StoredQueryDbModule extends AbstractFlyWayDbModule<StoredQueryConfig, StoredQueryDbConnProvider> {
    private static final String MODULE = "stroom-storedquery";
    private static final String FLYWAY_LOCATIONS = "stroom/storedquery/impl/db/migration";
    private static final String FLYWAY_TABLE = "query_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(StoredQueryDao.class).to(StoredQueryDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(StoredQueryDaoImpl.class);
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
    protected Class<StoredQueryDbConnProvider> getConnectionProviderType() {
        return StoredQueryDbConnProvider.class;
    }

    @Override
    protected StoredQueryDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements StoredQueryDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
