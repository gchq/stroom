package stroom.credentials.impl.db;

import stroom.credentials.api.CredentialsConfig.CredentialsDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

/**
 * Magic stuff to generate DB connection and Flyway integration.
 */
public class CredentialsDbModule extends AbstractFlyWayDbModule<CredentialsDbConfig, CredentialsDbConnProvider> {

    private static final String MODULE = "stroom-credentials";

    private static final String FLYWAY_LOCATIONS = "stroom/credentials/impl/db/migration";

    private static final String FLYWAY_TABLE = "credentials_schema_history";

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
    protected Class<CredentialsDbConnProvider> getConnectionProviderType() {
        return CredentialsDbConnProvider.class;
    }

    @Override
    protected CredentialsDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements CredentialsDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }

}
