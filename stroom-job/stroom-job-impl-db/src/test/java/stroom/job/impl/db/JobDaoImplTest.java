package stroom.job.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JobDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDaoImplTest.class);

    private static MySQLContainer dbContainer = new MySQLContainer()
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
    public void basicCreation() {
        Job job = createStandardJob();

        // Then
        assertThat(job.getId()).isNotNull();
        assertThat(job.getVersion()).isNotNull();
        assertThat(job.getDescription()).isEqualTo("Some description");
        assertThat(job.isEnabled()).isTrue();


        Job loadedJob = jobDao.fetch(job.getId()).get();
        assertThat(loadedJob.getId()).isEqualTo(job.getId());
        assertThat(loadedJob.getVersion()).isNotNull();
        assertThat(loadedJob.getDescription()).isEqualTo("Some description");
        assertThat(loadedJob.isEnabled()).isTrue();
    }

    @Test
    public void descriptionTooLong() {
        // Given
        Job job = new Job();
        job.setEnabled(true);
        job.setDescription(RandomStringUtils.randomAlphabetic(256));

        // When
        assertThrows(DataAccessException.class, () -> jobDao.create(job));
    }

    @Test
    public void badFetch(){
        Optional<Job> job = jobDao.fetch(11111);
        assertThat(job.isPresent()).isFalse();
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }

    private Job createStandardJob(){
        // Given
        Job job = new Job();
        job.setEnabled(true);
        job.setDescription("Some description");

        // When
        Job createdJob = jobDao.create(job);
        return job;
    }
}
