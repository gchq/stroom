package stroom.security.identity.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.security.identity.config.IdentityConfig.IdentityDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class IdentityDbModule extends AbstractFlyWayDbModule<IdentityDbConfig, IdentityDbConnProvider> {

    private static final String MODULE = "stroom-security-identity";
    private static final String FLYWAY_LOCATIONS = "stroom/security/identity/db/migration";
    private static final String FLYWAY_TABLE = "identity_schema_history";

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(IdentityDbConnProvider.class);
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
    protected Class<IdentityDbConnProvider> getConnectionProviderType() {
        return IdentityDbConnProvider.class;
    }

    @Override
    protected IdentityDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    public static class DataSourceImpl extends DataSourceProxy implements IdentityDbConnProvider {

        public DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
