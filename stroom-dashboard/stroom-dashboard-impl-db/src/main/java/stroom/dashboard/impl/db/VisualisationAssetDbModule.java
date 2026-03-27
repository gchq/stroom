package stroom.dashboard.impl.db;

import stroom.dashboard.impl.visualisation.VisualisationAssetConfig.VisualisationAssetDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class VisualisationAssetDbModule
        extends AbstractFlyWayDbModule<VisualisationAssetDbConfig, VisualisationAssetDbConnProvider> {

    /** Name of this module */
    private static final String MODULE = "stroom-dashboard";

    /** Where the Flyway SQL scripts are */
    private static final String FLYWAY_LOCATIONS = "stroom/dashboard/impl/db/migration";

    /** Table with the Flyway history */
    private static final String FLYWAY_TABLE = "visualisation_assets_schema_history";

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
    protected Class<VisualisationAssetDbConnProvider> getConnectionProviderType() {
        return VisualisationAssetDbConnProvider.class;
    }

    @Override
    protected VisualisationAssetDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements VisualisationAssetDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }

}
