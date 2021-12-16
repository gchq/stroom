package stroom.activity.impl.db;

import stroom.activity.impl.db.ActivityConfig.ActivityDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class ActivityDbModule extends AbstractFlyWayDbModule<ActivityDbConfig, ActivityDbConnProvider> {

    private static final String MODULE = "stroom-activity";
    private static final String FLYWAY_LOCATIONS = "stroom/activity/impl/db/migration";
    private static final String FLYWAY_TABLE = "activity_schema_history";

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(ActivityDbConnProvider.class);
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
    protected Class<ActivityDbConnProvider> getConnectionProviderType() {
        return ActivityDbConnProvider.class;
    }

    @Override
    protected ActivityDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements ActivityDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
