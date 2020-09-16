package stroom.security.impl;

import stroom.cache.impl.CacheModule;
import stroom.explorer.api.ExplorerService;
import stroom.security.impl.db.SecurityDbModule;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.mock.MockProcessingUserIdentityProviderModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceCoreMigration;
import stroom.util.entityevent.EntityEventBus;

import com.google.inject.AbstractModule;

import static org.mockito.Mockito.mock;

public class TestModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new CacheModule());
        install(new SecurityDbModule());
        install(new MockSecurityContextModule());
        install(new MockProcessingUserIdentityProviderModule());

        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ExplorerService.class).toInstance(mock(ExplorerService.class));
        bind(EntityEventBus.class).toInstance(mock(EntityEventBus.class));
        bind(PermissionChangeEventBus.class).toInstance(mock(PermissionChangeEventBus.class));

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() {});
    }
}
