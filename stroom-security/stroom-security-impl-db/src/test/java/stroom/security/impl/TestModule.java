package stroom.security.impl;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheModule;
import stroom.entity.shared.EntityEventBus;
import stroom.explorer.api.ExplorerService;
import stroom.security.impl.db.SecurityDbModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;

import static org.mockito.Mockito.mock;

public class TestModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new CacheModule());
        install(new SecurityDbModule());
        install(new MockSecurityContextModule());

        bind(UserService.class).to(UserServiceImpl.class);
        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(ExplorerService.class).toInstance(mock(ExplorerService.class));
        bind(EntityEventBus.class).toInstance(mock(EntityEventBus.class));
    }
}
