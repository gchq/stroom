package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticsConfig.AnalyticsDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class AnalyticsDbModule extends AbstractFlyWayDbModule<AnalyticsDbConfig, AnalyticsDbConnProvider> {

    private static final String MODULE = "stroom-analytics";
    private static final String FLYWAY_LOCATIONS = "stroom/analytics/impl/db/migration";
    private static final String FLYWAY_TABLE = "analytics_schema_history";

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
    protected Class<AnalyticsDbConnProvider> getConnectionProviderType() {
        return AnalyticsDbConnProvider.class;
    }

    @Override
    protected AnalyticsDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements AnalyticsDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
