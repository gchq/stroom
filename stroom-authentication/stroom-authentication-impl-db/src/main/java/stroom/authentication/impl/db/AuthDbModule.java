package stroom.authentication.impl.db;

import stroom.authentication.AuthenticationDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

public class AuthDbModule extends AbstractFlyWayDbModule<AuthenticationDbConfig, AuthDbConnProvider> {
    private static final String MODULE = "stroom-authentication";
    private static final String FLYWAY_LOCATIONS = "stroom/authentication/db/migration";
    private static final String FLYWAY_TABLE = "authentication_schema_history";

    @Override
    protected void configure() {
        super.configure();

        bind(TokenDao.class);
        bind(UserDao.class);
        bind(JwkDao.class);
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
    protected Class<AuthDbConnProvider> getConnectionProviderType() {
        return AuthDbConnProvider.class;
    }

    @Override
    protected AuthDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements AuthDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
