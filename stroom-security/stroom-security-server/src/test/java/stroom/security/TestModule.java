package stroom.security;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;
import org.testcontainers.containers.MySQLContainer;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.entity.event.EntityEventBus;
import stroom.explorer.api.ExplorerService;
import stroom.security.impl.SecurityImpl;
import stroom.security.impl.db.SecurityDbConfig;
import stroom.security.impl.db.SecurityDbModule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {
    private final MySQLContainer dbContainer;

    public TestModule(final MySQLContainer dbContainer) {
        this.dbContainer = dbContainer;
    }

    @Override
    protected void configure() {
        // We want the 'real' security DB Module
        install(new SecurityDbModule());

        if (null != dbContainer) {
            bind(SecurityDbConfig.class).toInstance(new SecurityDbConfig.Builder()
                    .withConnectionConfig(new ConnectionConfig.Builder()
                            .jdbcDriverClassName(dbContainer.getDriverClassName())
                            .password(dbContainer.getPassword())
                            .username(dbContainer.getUsername())
                            .jdbcUrl(dbContainer.getJdbcUrl())
                            .build())
                    .withConnectionPoolConfig(new ConnectionPoolConfig.Builder()
                            .build())
                    .build());
        } else {
            bind(SecurityDbConfig.class).toInstance(new SecurityDbConfig.Builder()
                    .withConnectionConfig(new ConnectionConfig.Builder()
                            .jdbcDriverClassName("com.mysql.jdbc.Driver")
                            .jdbcUrl("jdbc:mysql://localhost:14450/stroom?useUnicode=yes&characterEncoding=UTF-8")
                            .password("stroompassword1")
                            .username("stroomuser")
                            .build())
                    .withConnectionPoolConfig(new ConnectionPoolConfig.Builder()
                            .build())
                    .build());
        }

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
