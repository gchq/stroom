package stroom.query.field.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.query.field.impl.QueryFieldConfig.QueryDatasourceDbConfig;

import java.util.List;
import javax.sql.DataSource;

public class QueryFieldDbModule
        extends AbstractFlyWayDbModule<QueryDatasourceDbConfig, QueryFieldDbConnProvider> {

    private static final String MODULE = "stroom-query-field";
    private static final String FLYWAY_LOCATIONS = "stroom/query/field/impl/db/migration";
    private static final String FLYWAY_TABLE = "field_schema_history";

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
    protected Class<QueryFieldDbConnProvider> getConnectionProviderType() {
        return QueryFieldDbConnProvider.class;
    }

    @Override
    protected QueryFieldDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DatasourceImpl(dataSource);
    }

    private static class DatasourceImpl extends DataSourceProxy implements QueryFieldDbConnProvider {

        private DatasourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
