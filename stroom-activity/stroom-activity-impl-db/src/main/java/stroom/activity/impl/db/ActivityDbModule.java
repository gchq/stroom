package stroom.activity.impl.db;

import stroom.activity.impl.ActivityDao;
import stroom.activity.impl.ActivityModule;
import stroom.db.util.AbstractFlyWayDbModule;

import javax.sql.DataSource;
import java.util.function.Function;

public class ActivityDbModule extends AbstractFlyWayDbModule<ActivityConfig, ActivityDbConnProvider> {
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
    protected Function<DataSource, ActivityDbConnProvider> getConnectionProviderConstructor() {
        return ActivityDbConnProvider::new;
    }

    @Override
    protected Class<ActivityDbConnProvider> getConnectionProviderType() {
        return ActivityDbConnProvider.class;
    }
}
