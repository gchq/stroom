package stroom.index.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.index.dao.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexVolumeGroupDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexVolumeGroupDaoImplTest.class);

    private static MySQLContainer dbContainer = null;
    // = new MySQLContainer().withDatabaseName(Stroom.STROOM.getName());
    // = null;//

    private static IndexVolumeGroupDao indexVolumeGroupDao;

    @BeforeAll
    public static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        final Injector injector = Guice.createInjector(new IndexDbModule(), new TestModule(dbContainer));

        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
    }

    @AfterAll
    public static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }

    @Test
    public void testCreateGetDelete() {
        // Given
        final Long now = System.currentTimeMillis();
        final String groupName = TestData.createVolumeGroupName();

        // When
        final IndexVolumeGroup created = indexVolumeGroupDao.create(groupName);
        final IndexVolumeGroup retrieved = indexVolumeGroupDao.get(groupName);

        // Then
        assertThat(Stream.of(created, retrieved)).allSatisfy(i -> {
            assertThat(i).isNotNull();
            assertThat(i.getName()).isEqualTo(groupName);
            assertThat(i.getCreateUser()).isEqualTo(TestModule.TEST_USER);
            assertThat(i.getCreateTimeMs()).isCloseTo(now, Offset.offset(100L));
        });

        indexVolumeGroupDao.delete(groupName);

        final IndexVolumeGroup retrievedAfterDelete = indexVolumeGroupDao.get(groupName);
        assertThat(retrievedAfterDelete).isNull();
    }

    @Test
    public void testCreateWithDuplicateName() {
        // Given
        final Long now = System.currentTimeMillis();
        final String groupName = TestData.createVolumeGroupName();

        // When
        final IndexVolumeGroup createdOnce = indexVolumeGroupDao.create(groupName);
        final Long createdTimeMs = createdOnce.getCreateTimeMs();
        assertThat(createdTimeMs).isCloseTo(now, Offset.offset(100L));
        final IndexVolumeGroup retrievedOnce = indexVolumeGroupDao.get(groupName);

        // Put some delay in it, so that the audit time fields will definitely be different
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final IndexVolumeGroup createdTwice = indexVolumeGroupDao.create(groupName);
        final IndexVolumeGroup retrievedTwice = indexVolumeGroupDao.get(groupName);

        // Make sure they are all the same
        assertThat(Stream.of(createdOnce, retrievedOnce, createdTwice, retrievedTwice))
                .allSatisfy(i -> {
                    assertThat(i.getCreateTimeMs()).isEqualTo(createdTimeMs);
                    assertThat(i.getUpdateTimeMs()).isEqualTo(createdTimeMs);
                    assertThat(i.getCreateUser()).isEqualTo(TestModule.TEST_USER);
                    assertThat(i.getUpdateUser()).isEqualTo(TestModule.TEST_USER);
                });
    }
}
