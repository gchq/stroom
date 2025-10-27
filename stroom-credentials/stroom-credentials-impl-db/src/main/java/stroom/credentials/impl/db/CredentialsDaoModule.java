package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

/**
 * Guice binding for the Credentials DAO.
 */
public class CredentialsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        bind(CredentialsDao.class).to(CredentialsDaoImpl.class);
        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(CredentialsDaoImpl.class);
    }
}
