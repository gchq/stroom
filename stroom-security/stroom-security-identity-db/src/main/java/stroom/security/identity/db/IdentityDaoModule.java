package stroom.security.identity.db;

import stroom.security.identity.account.AccountDao;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.identity.token.ApiKeyDao;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.KeyTypeDao;

import com.google.inject.AbstractModule;

public class IdentityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ApiKeyDao.class).to(ApiKeyDaoImpl.class);
        bind(AccountDao.class).to(AccountDaoImpl.class);
        bind(JwkDao.class).to(JwkDaoImpl.class);
        bind(KeyTypeDao.class).to(KeyTypeDaoImpl.class);
        bind(OpenIdClientDao.class).to(OpenIdClientDaoImpl.class);
    }
}
