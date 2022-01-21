package stroom.security.impl.db;

import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDao;

import com.google.inject.AbstractModule;

public class SecurityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(UserDao.class).to(UserDaoImpl.class);
        bind(DocumentPermissionDao.class).to(DocumentPermissionDaoImpl.class);
        bind(AppPermissionDao.class).to(AppPermissionDaoImpl.class);
    }
}
