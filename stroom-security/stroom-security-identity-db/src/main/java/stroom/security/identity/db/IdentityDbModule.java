package stroom.security.identity.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.config.IdentityConfig.IdentityDbConfig;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenTypeDao;

import javax.sql.DataSource;

public class IdentityDbModule extends AbstractFlyWayDbModule<IdentityDbConfig, IdentityDbConnProvider> {

    private static final String MODULE = "stroom-security-identity";
    private static final String FLYWAY_LOCATIONS = "stroom/security/identity/db/migration";
    private static final String FLYWAY_TABLE = "identity_schema_history";

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
