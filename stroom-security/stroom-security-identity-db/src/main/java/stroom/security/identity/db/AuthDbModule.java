package stroom.security.identity.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenTypeDao;

import javax.sql.DataSource;

public class AuthDbModule extends AbstractFlyWayDbModule<IdentityConfig, AuthDbConnProvider> {
    private static final String MODULE = "stroom-authentication";
    private static final String FLYWAY_LOCATIONS = "stroom/security/identity/db/migration";
    private static final String FLYWAY_TABLE = "authentication_schema_history";

    @Override
    protected void configure() {
        super.configure();

        bind(TokenDao.class).to(TokenDaoImpl.class);
        bind(AccountDao.class).to(AccountDaoImpl.class);
        bind(JwkDao.class).to(JwkDaoImpl.class);
        bind(TokenTypeDao.class).to(TokenTypeDaoImpl.class);
        bind(OpenIdClientDao.class).to(OpenIdClientDaoImpl.class);
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

    public static class DataSourceImpl extends DataSourceProxy implements AuthDbConnProvider {
        public DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
