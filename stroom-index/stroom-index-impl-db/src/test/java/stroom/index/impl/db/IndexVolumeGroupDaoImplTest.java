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
import stroom.index.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IndexVolumeGroupDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexVolumeGroupDaoImplTest.class);

    private static MySQLContainer dbContainer = null;
    // = new MySQLContainer().withDatabaseName(Stroom.STROOM.getName());
    // = null;//

    private static IndexVolumeGroupDao indexVolumeGroupDao;

    @BeforeAll
    static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        final Injector injector = Guice.createInjector(new IndexDbModule(), new TestModule(dbContainer));

        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
    }

    @AfterAll
    static void afterAll() {
        LOGGER.info(() -> "After All - Stop Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::stop);
    }

    @Test
    void getNamesAndAll() {
        // Given
        final long now = System.currentTimeMillis();
        final Set<String> namesToDelete = IntStream.range(0, 3)
                .mapToObj(TestData::createVolumeGroupName).collect(Collectors.toSet());
        final Set<String> names = IntStream.range(0, 7)
                .mapToObj(TestData::createVolumeGroupName).collect(Collectors.toSet());

        // When
        namesToDelete.forEach(indexVolumeGroupDao::create);
        names.forEach(indexVolumeGroupDao::create);
        final List<String> allNames1 = indexVolumeGroupDao.getNames();
        final List<IndexVolumeGroup> allGroups1 = indexVolumeGroupDao.getAll();
        final List<IndexVolumeGroup> foundGroups1 = Stream.concat(namesToDelete.stream(), names.stream())
                .map(indexVolumeGroupDao::get)
                .collect(Collectors.toList());

        namesToDelete.forEach(indexVolumeGroupDao::delete);
        final List<String> allNames2 = indexVolumeGroupDao.getNames();
        final List<IndexVolumeGroup> allGroups2 = indexVolumeGroupDao.getAll();
        final List<IndexVolumeGroup> foundGroups2 = names.stream()
                .map(indexVolumeGroupDao::get)
                .collect(Collectors.toList());

        // Then
        assertThat(allNames1).containsAll(namesToDelete);
        assertThat(allNames1).containsAll(names);
        assertThat(foundGroups1.stream().map(IndexVolumeGroup::getName))
                .containsAll(namesToDelete);
        assertThat(foundGroups1.stream().map(IndexVolumeGroup::getName))
                .containsAll(names);

        assertThat(allNames2).doesNotContainAnyElementsOf(namesToDelete);
        assertThat(allNames2).containsAll(names);
        assertThat(allGroups2.stream().map(IndexVolumeGroup::getName))
                .doesNotContainAnyElementsOf(namesToDelete);
        assertThat(allGroups2.stream().map(IndexVolumeGroup::getName))
                .containsAll(names);

        final Consumer<IndexVolumeGroup> checkAuditFields = indexVolumeGroup -> {
            assertThat(indexVolumeGroup.getCreateUser())
                    .isEqualTo(TestModule.TEST_USER);
            assertThat(indexVolumeGroup.getUpdateUser())
                    .isEqualTo(TestModule.TEST_USER);
            assertThat(indexVolumeGroup.getCreateTimeMs())
                    .isCloseTo(now, Offset.offset(1000L));
            assertThat(indexVolumeGroup.getUpdateTimeMs())
                    .isCloseTo(now, Offset.offset(1000L));
        };
        assertThat(foundGroups1).allSatisfy(checkAuditFields);
        assertThat(foundGroups2).allSatisfy(checkAuditFields);
    }

    @Test
    void testCreateGetDelete() {
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
            assertThat(i.getCreateTimeMs()).isCloseTo(now, Offset.offset(1000L));
        });

        indexVolumeGroupDao.delete(groupName);

        final IndexVolumeGroup retrievedAfterDelete = indexVolumeGroupDao.get(groupName);
        assertThat(retrievedAfterDelete).isNull();
    }

    @Test
    void testCreateWithDuplicateName() {
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
