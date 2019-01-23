package stroom.job.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.DataChangedException;
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
        // Given/when
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
    public void quickCreation(){
        // Given/when
        Job job = jobDao.create();

        // Then
        assertThat(job.getDescription()).isNullOrEmpty();
        assertThat(job.isEnabled()).isFalse();
        assertThat(job.getId()).isNotNull();
        assertThat(job.getVersion()).isNotNull();
    }

    @Test
    public void descriptionTooLong() {
        // Given
        Job job = new Job();
        job.setEnabled(true);
        job.setDescription(RandomStringUtils.randomAlphabetic(256));

        // When/then
        assertThrows(DataAccessException.class, () -> jobDao.create(job));
    }

    @Test
    public void badFetch(){
        // Given/when
        Optional<Job> job = jobDao.fetch(11111);
        // Then
        assertThat(job.isPresent()).isFalse();
    }

    @Test
    public void update(){
        // Given
        Job job = createStandardJob();
        int version = job.getVersion();
        job.setDescription("Different description");
        job.setEnabled(false);

        // When
        Job updatedJob = jobDao.update(job);

        // Then
        assertThat(updatedJob.getId()).isEqualTo(job.getId());
        assertThat(updatedJob.getVersion()).isEqualTo(version + 1);
        assertThat(updatedJob.getDescription()).isEqualTo("Different description");
        assertThat(updatedJob.isEnabled()).isFalse();

        // Then
        Job fetchedUpdatedJob = jobDao.fetch(updatedJob.getId()).get();
        assertThat(fetchedUpdatedJob.getId()).isEqualTo(job.getId());
        assertThat(fetchedUpdatedJob.getVersion()).isEqualTo(version + 1);
        assertThat(fetchedUpdatedJob.getDescription()).isEqualTo("Different description");
        assertThat(fetchedUpdatedJob.isEnabled()).isFalse();
    }

    @Test
    public void delete() {
        // Given
        Job job = createStandardJob();

        // When
        int numberOfDeletedRecords = jobDao.delete(job.getId());

        // Then
        assertThat(numberOfDeletedRecords).isEqualTo(1);
        Optional<Job> optionalJob = jobDao.fetch(job.getId());
        assertThat(optionalJob.isPresent()).isFalse();
    }

    @Test
    public void badDelete(){
        // Given/when
        int numberOfDeletedRecords = jobDao.delete(111111);
        // Then
        assertThat(numberOfDeletedRecords).isEqualTo(0);
    }

    @Test
    public void checkOcc(){
        // Given
        Job job = createStandardJob();
        Job copy1 = jobDao.fetch(job.getId()).get();
        Job copy2 = jobDao.fetch(job.getId()).get();

        copy1.setDescription("change 1");
        jobDao.update(copy1);

        copy2.setDescription("change 2");

        // When/then
        assertThrows(DataChangedException.class, () -> jobDao.update(copy2));
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }

    private Job createStandardJob(){
        Job job = new Job();
        job.setEnabled(true);
        job.setDescription("Some description");
        Job createdJob = jobDao.create(job);
        return createdJob;
    }
}
