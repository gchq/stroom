package stroom.storedquery.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.storedquery.impl.StoredQueryModule;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import java.util.function.Function;

public class StoredQueryDbModule extends AbstractFlyWayDbModule<StoredQueryConfig, StoredQueryDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoredQueryModule.class);
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
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, StoredQueryDbConnProvider> getConnectionProviderConstructor() {
        return StoredQueryDbConnProvider::new;
    }

    @Override
    public Class<StoredQueryDbConnProvider> getConnectionProviderType() {
        return StoredQueryDbConnProvider.class;
    }
}
