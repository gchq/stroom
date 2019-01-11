package stroom.security.impl.db;

import com.google.inject.AbstractModule;
import org.testcontainers.containers.MySQLContainer;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

class ContainerSecurityConfigModule extends AbstractModule {
    private final MySQLContainer dbContainer;

    public ContainerSecurityConfigModule(final MySQLContainer dbContainer) {
        this.dbContainer = dbContainer;
    }

    @Override
    protected void configure() {
        bind(SecurityDbConfig.class).toInstance(new SecurityDbConfig.Builder()
                .withConnectionConfig(new ConnectionConfig.Builder()
                        .withJdbcDriverClassName(dbContainer.getDriverClassName())
                        .withJdbcDriverPassword(dbContainer.getPassword())
                        .withJdbcDriverUsername(dbContainer.getUsername())
                        .withJdbcDriverUrl(dbContainer.getJdbcUrl())
                        .build())
                .withConnectionPoolConfig(new ConnectionPoolConfig.Builder()
                        .build())
                .build());
    }
}
