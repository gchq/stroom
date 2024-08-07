package stroom.activity.impl.db;

import stroom.activity.impl.db.ActivityConfig.ActivityDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class ActivityDbModule extends AbstractFlyWayDbModule<ActivityDbConfig, ActivityDbConnProvider> {

    private static final String MODULE = "stroom-activity";
    private static final String FLYWAY_LOCATIONS = "stroom/activity/impl/db/migration";
    private static final String FLYWAY_TABLE = "activity_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
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
            super(dataSource, MODULE);
        }
    }
}
