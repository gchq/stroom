package stroom.security.impl.db;

import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.AppPermissionIdDao;
import stroom.security.impl.DocTypeIdDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDao;
import stroom.security.impl.apikey.ApiKeyDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class SecurityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ApiKeyDao.class).to(ApiKeyDaoImpl.class);
        bind(UserDao.class).to(UserDaoImpl.class);
        bind(DocumentPermissionDao.class).to(DocumentPermissionDaoImpl.class);
        bind(AppPermissionDao.class).to(AppPermissionDaoImpl.class);
        bind(AppPermissionIdDao.class).to(AppPermissionIdDaoImpl.class);
        bind(DocTypeIdDao.class).to(DocTypeIdDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(AppPermissionIdDaoImpl.class)
                .addBinding(DocTypeIdDaoImpl.class);
    }
}
