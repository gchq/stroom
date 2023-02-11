package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationConfig.AnnotationDBConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class AnnotationDbModule extends AbstractFlyWayDbModule<AnnotationDBConfig, AnnotationDbConnProvider> {

    private static final String MODULE = "stroom-annotation";
    private static final String FLYWAY_LOCATIONS = "stroom/annotation/impl/db/migration";
    private static final String FLYWAY_TABLE = "annotation_schema_history";

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
    protected Class<AnnotationDbConnProvider> getConnectionProviderType() {
        return AnnotationDbConnProvider.class;
    }

    @Override
    protected AnnotationDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements AnnotationDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
