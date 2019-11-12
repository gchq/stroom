package stroom.config.global.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.PropertyServiceConfig;
import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.global.impl.GlobalConfigModule;
import stroom.db.util.AbstractFlyWayDbModule;

import java.util.function.Function;

public class GlobalConfigDbModule extends AbstractFlyWayDbModule<PropertyServiceConfig, GlobalConfigDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigDbModule.class);
    private static final String MODULE = "stroom-config";
    private static final String FLYWAY_LOCATIONS = "stroom/config/global/impl/db/migration";
    private static final String FLYWAY_TABLE = "config_schema_history";

    @Override
    protected void configure() {
        super.configure();
        install(new GlobalConfigModule());

        bind(ConfigPropertyDao.class).to(ConfigPropertyDaoImpl.class);
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
    public Function<HikariConfig, GlobalConfigDbConnProvider> getConnectionProviderConstructor() {
        return GlobalConfigDbConnProvider::new;
    }

    @Override
    public Class<GlobalConfigDbConnProvider> getConnectionProviderType() {
        return GlobalConfigDbConnProvider.class;
    }
}
