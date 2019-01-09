package stroom.security.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

public class UserDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer();

    private static Injector injector;
    private static UserDao userDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        dbContainer.start();

        injector = Guice.createInjector(new SecurityDbModule(), new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();

                bind(SecurityDbConfig.class)
                        .toInstance(new SecurityDbConfig.Builder()
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
        });

        userDao = injector.getInstance(UserDao.class);
    }

    @Test
    public void aTest() {
        LOGGER.info(() -> "A Test Run");
        userDao.createUser("Guybrush Threepwood");
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        dbContainer.stop();
    }
}
