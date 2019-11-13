package stroom.activity.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.impl.ActivityDao;
import stroom.activity.impl.ActivityModule;
import stroom.db.util.AbstractFlyWayDbModule;

import java.util.function.Function;

public class ActivityDbModule extends AbstractFlyWayDbModule<ActivityConfig, ActivityDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityDbModule.class);
    private static final String MODULE = "stroom-activity";
    private static final String FLYWAY_LOCATIONS = "stroom/activity/impl/db/migration";
    private static final String FLYWAY_TABLE = "activity_schema_history";

    @Override
    protected void configure() {
        super.configure();
        install(new ActivityModule());

        bind(ActivityDao.class).to(ActivityDaoImpl.class);
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
    public Function<HikariConfig, ActivityDbConnProvider> getConnectionProviderConstructor() {
        return ActivityDbConnProvider::new;
    }

    @Override
    public Class<ActivityDbConnProvider> getConnectionProviderType() {
        return ActivityDbConnProvider.class;
    }
}
