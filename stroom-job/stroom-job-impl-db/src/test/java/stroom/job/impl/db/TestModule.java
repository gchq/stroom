package stroom.job.impl.db;

import com.google.inject.AbstractModule;
import org.testcontainers.containers.MySQLContainer;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

class TestModule extends AbstractModule {
    private final MySQLContainer dbContainer;

    public TestModule(final MySQLContainer dbContainer) {
        this.dbContainer = dbContainer;
    }

    @Override
    protected void configure() {
        if (null != dbContainer) {
            bind(JobDbConfig.class).toInstance(new JobDbConfig.Builder()
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
            bind(JobDbConfig.class).toInstance(new JobDbConfig.Builder()
                    .withConnectionConfig(new ConnectionConfig.Builder()
                            .jdbcDriverClassName("com.mysql.jdbc.Driver")
                            .jdbcUrl("jdbc:mysql://localhost:14450/stroom?useUnicode=yes&characterEncoding=UTF-8")
                            .password("stroomuser")
                            .username("stroompassword1")
                            .build())
                    .withConnectionPoolConfig(new ConnectionPoolConfig.Builder()
                            .build())
                    .build());
        }
    }
}
