package stroom.security.identity.db;

import stroom.security.identity.account.AccountDao;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenTypeDao;

import com.google.inject.AbstractModule;

public class IdentityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(TokenDao.class).to(TokenDaoImpl.class);
        bind(AccountDao.class).to(AccountDaoImpl.class);
        bind(JwkDao.class).to(JwkDaoImpl.class);
        bind(TokenTypeDao.class).to(TokenTypeDaoImpl.class);
        bind(OpenIdClientDao.class).to(OpenIdClientDaoImpl.class);
    }
}
