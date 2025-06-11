package stroom.security.impl;

import stroom.activity.api.ActivityService;
import stroom.cache.impl.CacheModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.explorer.api.ExplorerService;
import stroom.security.api.AppPermissionService;
import stroom.security.api.UserService;
import stroom.security.impl.db.SecurityDaoModule;
import stroom.security.impl.db.SecurityDbModule;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.mock.MockHasUserDependenciesModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.mock.MockUserIdentityFactoryModule;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.entityevent.EntityEventBus;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new MockMetricsModule());
        install(new CacheModule());
        install(new SecurityDbModule());
        install(new SecurityDaoModule());
        install(new MockSecurityContextModule());
        install(new MockUserIdentityFactoryModule());
        install(new MockTaskModule());
        install(new MockHasUserDependenciesModule());

        install(new MockCollectionModule());
        install(new MockDocRefInfoModule());
        install(new MockWordListProviderModule());

        bind(AppPermissionService.class).to(AppPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ExplorerService.class).toInstance(mock(ExplorerService.class));
        bind(EntityEventBus.class).toInstance(mock(EntityEventBus.class));
        bind(PermissionChangeEventBus.class).toInstance(mock(PermissionChangeEventBus.class));

        final StoredQueryService storedQueryService = GuiceTestUtil.bindMock(binder(), StoredQueryService.class);
        Mockito.when(storedQueryService.deleteByOwner(Mockito.any())).thenReturn(0);

        final UserPreferencesService userPreferencesService = GuiceTestUtil.bindMock(
                binder(), UserPreferencesService.class);
        Mockito.when(userPreferencesService.delete(Mockito.any())).thenReturn(true);

        final ActivityService activityService = GuiceTestUtil.bindMock(binder(), ActivityService.class);
        Mockito.when(activityService.deleteAllByOwner(Mockito.any())).thenReturn(0);
    }
}
