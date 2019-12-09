package stroom.storedquery.impl.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import java.util.function.Function;

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
    protected Function<HikariConfig, StoredQueryDbConnProvider> getConnectionProviderConstructor() {
        return StoredQueryDbConnProvider::new;
    }

    @Override
    protected Class<StoredQueryDbConnProvider> getConnectionProviderType() {
        return StoredQueryDbConnProvider.class;
    }
}
