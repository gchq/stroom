package stroom.security.impl;

import stroom.cache.impl.CacheModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.explorer.api.ExplorerService;
import stroom.security.impl.db.SecurityDaoModule;
import stroom.security.impl.db.SecurityDbModule;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.mock.MockUserIdentityFactoryModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceLegacyMigration;
import stroom.util.entityevent.EntityEventBus;

import com.google.inject.AbstractModule;

import static org.mockito.Mockito.mock;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new CacheModule());
        install(new SecurityDbModule());
        install(new SecurityDaoModule());
        install(new MockSecurityContextModule());
        install(new MockUserIdentityFactoryModule());
        install(new MockTaskModule());

        install(new MockCollectionModule());
        install(new MockDocRefInfoModule());
        install(new MockWordListProviderModule());

        bind(AppPermissionService.class).to(AppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ExplorerService.class).toInstance(mock(ExplorerService.class));
        bind(EntityEventBus.class).toInstance(mock(EntityEventBus.class));
        bind(PermissionChangeEventBus.class).toInstance(mock(PermissionChangeEventBus.class));

        // Not using all the DB modules so just bind to an empty anonymous class
        bind(ForceLegacyMigration.class).toInstance(new ForceLegacyMigration() {
        });
    }
}
