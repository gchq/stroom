package stroom.index.impl.db;

import com.google.inject.AbstractModule;
import org.testcontainers.containers.MySQLContainer;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.security.api.SecurityContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {
    private final MySQLContainer dbContainer;
    static final String TEST_USER = "testUser";

    public TestModule(final MySQLContainer dbContainer) {
        this.dbContainer = dbContainer;
    }

    @Override
    protected void configure() {
        // Create a test security context
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserId()).thenReturn(TEST_USER);
        bind(SecurityContext.class).toInstance(securityContext);

        if (null != dbContainer) {
            bind(IndexDbConfig.class).toInstance(new IndexDbConfig.Builder()
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
            // This should use the stroom-all-dbs setup by stroom resources
            bind(IndexDbConfig.class).toInstance(new IndexDbConfig.Builder()
                    .withConnectionConfig(new ConnectionConfig.Builder()
                            .jdbcDriverClassName("com.mysql.jdbc.Driver")
                            .jdbcUrl("jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8")
                            .password("stroompassword1")
                            .username("stroomuser")
                            .build())
                    .withConnectionPoolConfig(new ConnectionPoolConfig.Builder()
                            .build())
                    .build());
        }
    }
}
