package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

public class AppPermissionDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer();

    private static Injector injector;
    private static AppPermissionDao appPermissionDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        dbContainer.start();

        injector = Guice.createInjector(new SecurityDbModule(), new ContainerSecurityConfigModule(dbContainer));

        appPermissionDao = injector.getInstance(AppPermissionDao.class);
    }

    @Test
    public void aTest() {

    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        dbContainer.stop();
    }
}
