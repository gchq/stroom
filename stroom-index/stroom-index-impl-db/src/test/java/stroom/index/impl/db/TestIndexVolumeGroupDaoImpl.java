package stroom.index.impl.db;

import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeGroupDaoImpl {

    private static IndexVolumeGroupDao indexVolumeGroupDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
    }

    @Test
    void testCreate() {
        // Given
        final var groupName = TestData.createVolumeGroupName();

        // When
        final var group = createGroup(groupName);

        // Then
        assertThat(group.getName()).isEqualTo(groupName);
    }


    @Test
    void testUpdate() {
        // Given
        final var groupName = TestData.createVolumeGroupName();
        final var group = createGroup(groupName);
        final var newGroupName = TestData.createVolumeGroupName();
        final var reloadedGroup = indexVolumeGroupDao.get(group.getId());

        // When
        reloadedGroup.setName(newGroupName);
        indexVolumeGroupDao.update(reloadedGroup);

        // Then
        final var updatedGroup = indexVolumeGroupDao.get(group.getId());
        assertThat(updatedGroup).isNotNull();
        assertThat(updatedGroup.getName()).isEqualTo(newGroupName);
    }


    @Test
    void testDelete() {
        // Given
        final var groupName = TestData.createVolumeGroupName();
        final var group = createGroup(groupName);

        // When
        indexVolumeGroupDao.delete(group.getId());

        // Then
        final var deletedGroup = indexVolumeGroupDao.get(group.getId());
        assertThat(deletedGroup).isNull();
    }


    @Test
    void testCreateGetDelete() {
        // Given
        final Long now = System.currentTimeMillis();
        final String groupName = TestData.createVolumeGroupName();

        // When
        final IndexVolumeGroup created = createGroup(groupName);
        final IndexVolumeGroup retrieved = indexVolumeGroupDao.get(created.getId());

        // Then
        assertThat(Stream.of(created, retrieved)).allSatisfy(i -> {
            assertThat(i).isNotNull();
            assertThat(i.getName()).isEqualTo(groupName);
            assertThat(i.getCreateUser()).isEqualTo(TestModule.TEST_USER);
            assertThat(i.getCreateTimeMs()).isCloseTo(now, Offset.offset(1000L));
        });

        indexVolumeGroupDao.delete(created.getId());

        final IndexVolumeGroup retrievedAfterDelete = indexVolumeGroupDao.get(created.getId());
        assertThat(retrievedAfterDelete).isNull();
    }

    @Test
    void testCreateWithDuplicateName() {
        // Given
        final Long now = System.currentTimeMillis();
        final String groupName = TestData.createVolumeGroupName();

        // When
        final IndexVolumeGroup createdOnce = createGroup(groupName);
        final Long createdTimeMs = createdOnce.getCreateTimeMs();
        assertThat(createdTimeMs).isCloseTo(now, Offset.offset(100L));
        final IndexVolumeGroup retrievedOnce = indexVolumeGroupDao.get(createdOnce.getId());

        // Put some delay in it, so that the audit time fields will definitely be different
        try {
            Thread.sleep(100);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final IndexVolumeGroup createdTwice = createGroup(groupName);
        final IndexVolumeGroup retrievedTwice = indexVolumeGroupDao.get(createdTwice.getId());

        // Make sure they are all the same
        assertThat(Stream.of(createdOnce, retrievedOnce, createdTwice, retrievedTwice))
                .allSatisfy(i -> {
                    assertThat(i.getCreateTimeMs()).isEqualTo(createdTimeMs);
                    assertThat(i.getUpdateTimeMs()).isEqualTo(createdTimeMs);
                    assertThat(i.getCreateUser()).isEqualTo(TestModule.TEST_USER);
                    assertThat(i.getUpdateUser()).isEqualTo(TestModule.TEST_USER);
                });
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(() -> TestModule.TEST_USER, indexVolumeGroup);
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }
}
