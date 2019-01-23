package stroom.job.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class JobDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
//            .withUsername("stroomuser")
//            .withPassword("stroompassword1")
            .withDatabaseName("stroom");

    private static Injector injector;
    private static JobDao jobDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        injector = Guice.createInjector(new JobDbModule(), new TestModule(dbContainer));

        jobDao = injector.getInstance(JobDao.class);
    }

    @Test
    public void testJobCreation() {
        // Given
        Job job = new Job();
        job.setAdvanced(false);
        job.setEnabled(true);
        job.setDescription("Some description");

        // When
        Job createdJob = jobDao.create(job);
        assertThat(createdJob.getId()).isNotNull();
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }
}
