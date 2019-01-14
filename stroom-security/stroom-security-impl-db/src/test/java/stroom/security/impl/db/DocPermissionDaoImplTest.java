package stroom.security.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

import java.util.Optional;

public class DocPermissionDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImplTest.class);

    private static MySQLContainer dbContainer = null; //new MySQLContainer();

    private static Injector injector;
    private static DocumentPermissionDao documentPermissionDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        injector = Guice.createInjector(new SecurityDbModule(), new ContainerSecurityConfigModule(dbContainer));

        documentPermissionDao = injector.getInstance(DocumentPermissionDao.class);
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
