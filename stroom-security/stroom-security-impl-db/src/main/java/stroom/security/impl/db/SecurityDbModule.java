package stroom.security.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.security.impl.AuthorisationConfig.AuthorisationDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class SecurityDbModule extends AbstractFlyWayDbModule<AuthorisationDbConfig, SecurityDbConnProvider> {

    private static final String MODULE = "stroom-security";
    private static final String FLYWAY_LOCATIONS = "stroom/security/impl/db/migration";
    private static final String FLYWAY_TABLE = "security_schema_history";

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(SecurityDbConnProvider.class);
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
    protected Class<SecurityDbConnProvider> getConnectionProviderType() {
        return SecurityDbConnProvider.class;
    }

    @Override
    protected SecurityDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements SecurityDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
