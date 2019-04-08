package stroom.security.impl;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;
import stroom.cache.impl.CacheModule;
import stroom.entity.shared.EntityEventBus;
import stroom.explorer.api.ExplorerService;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.impl.db.SecurityDbModule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestModule extends AbstractModule {
    @Override
    protected void configure() {
        // We want the 'real' security DB Module
        install(new CacheModule());
        install(new SecurityDbModule());

        bind(UserService.class).to(UserServiceImpl.class);
        bind(UserAppPermissionService.class).to(UserAppPermissionServiceImpl.class);
        bind(DocumentPermissionService.class).to(DocumentPermissionServiceImpl.class);

        bind(Security.class).to(SecurityImpl.class);
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserId()).thenReturn("admin");
        when(securityContext.isLoggedIn()).thenReturn(true);
        when(securityContext.hasAppPermission(Mockito.any())).thenReturn(true);
        when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
        bind(SecurityContext.class).toInstance(securityContext);

        bind(ExplorerService.class).toInstance(mock(ExplorerService.class));
        bind(EntityEventBus.class).toInstance(mock(EntityEventBus.class));
    }
}
