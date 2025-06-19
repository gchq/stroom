package stroom.index.impl.db;

import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestIndexVolumeDaoImpl {

    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
    }

    @Test
    void testCreate() {
        // Given
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        final var indexVolumeGroup = createGroup(TestData.createVolumeGroupName());

        // When
        final IndexVolume created = createVolume(nodeName, path, indexVolumeGroup.getId());
        assertThat(created).isNotNull();
        final IndexVolume retrieved = indexVolumeDao.fetch(created.getId()).orElse(null);
        assertThat(Stream.of(created, retrieved)).allSatisfy(i -> {
            assertThat(i.getNodeName()).isEqualTo(nodeName);
            assertThat(i.getPath()).isEqualTo(path);
        });
        indexVolumeDao.delete(retrieved.getId());
        final IndexVolume retrievedAfterDelete = indexVolumeDao.fetch(created.getId()).orElse(null);

        assertThat(retrievedAfterDelete).isNull();
    }

    @Test
    void testUpdate() {
        // Given
        final var nodeName = TestData.createNodeName();
        final var path = TestData.createPath();
        final var indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final var indexVolume = createVolume(nodeName, path, indexVolumeGroup.getId());

        final var newNodeName = TestData.createNodeName();
        final var newPath = TestData.createPath();

        indexVolume.setNodeName(newNodeName);
        indexVolume.setPath(newPath);

        // When
        final var updatedIndexVolume = indexVolumeDao.update(indexVolume);

        // Then
        assertThat(updatedIndexVolume.getNodeName()).isEqualTo(newNodeName);
        assertThat(updatedIndexVolume.getPath()).isEqualTo(newPath);
    }


    @Test
    void testDelete() {
        // Given
        final var nodeName = TestData.createNodeName();
        final var path = TestData.createPath();
        final var indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final var indexVolume = createVolume(nodeName, path, indexVolumeGroup.getId());

        // When
        indexVolumeDao.delete(indexVolume.getId());

        // Then
        final var deletedVolumeOptional = indexVolumeDao.fetch(indexVolume.getId());

        assertThat(deletedVolumeOptional.isPresent()).isFalse();
    }

    @Test
    void testGetAll() {
        // Given
        final var indexVolumeGroup01 = createGroup(TestData.createVolumeGroupName());
        final var indexVolumeGroup02 = createGroup(TestData.createVolumeGroupName());
        final var indexVolumeGroup03 = createGroup(TestData.createVolumeGroupName());
        createVolume(indexVolumeGroup01.getId());
        createVolume(indexVolumeGroup01.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup03.getId());

        // When
        final var indexVolumes = indexVolumeDao.getAll();

        // Then
        final BiConsumer<Integer, Integer> checkTheNewVolumeExists = (id, expectedCount) -> {
            final var foundIndexVolumesForGroup = indexVolumes.stream()
                    .filter(indexVolume -> indexVolume.getIndexVolumeGroupId().equals(id)).collect(Collectors.toList());
            assertThat(foundIndexVolumesForGroup.size()).isEqualTo(expectedCount);
        };

        checkTheNewVolumeExists.accept(indexVolumeGroup01.getId(), 2);
        checkTheNewVolumeExists.accept(indexVolumeGroup02.getId(), 4);
        checkTheNewVolumeExists.accept(indexVolumeGroup03.getId(), 1);

        // We're only going to assert that our volumes are there, because this test class doesn't clean up
        // the DB or use a test container.
//        final var foundIndexVolumesForGroup01 = indexVolumes.stream().filter(indexVolume ->
//        indexVolume.getIndexVolumeGroupId() == indexVolumeGroup01.getId()).collect(Collectors.toList());
//        assertThat(foundIndexVolumesForGroup01.size()).isEqualTo(2);
//        final var foundIndexVolumesForGroup02 = indexVolumes.stream().filter(indexVolume ->
//        indexVolume.getId() == indexVolumeGroup02.getId()).collect(Collectors.toList());
//        assertThat(foundIndexVolumesForGroup02.size()).isEqualTo(4);
//        final var foundIndexVolumesForGroup03 = indexVolumes.stream().filter(indexVolume ->
//        indexVolume.getId() == indexVolumeGroup03.getId()).collect(Collectors.toList());
//        assertThat(foundIndexVolumesForGroup03.size()).isEqualTo(1);
    }

    @Test
    void testMustHaveGroup() {
        // Given
        final var indexVolumeGroup01 = createGroup(TestData.createVolumeGroupName());
        final var indexVolume = createVolume(indexVolumeGroup01.getId());

        // When / then
        indexVolume.setIndexVolumeGroupId(null);
        assertThrows(DataAccessException.class, () -> indexVolumeDao.update(indexVolume));
    }

    private IndexVolume createVolume(final int indexVolumeGroupId) {
        final var nodeName = TestData.createNodeName();
        final var path = TestData.createPath();
        return createVolume(nodeName, path, indexVolumeGroupId);
    }

    private IndexVolume createVolume(final String nodeName, final String path, final int indexVolumeGroupId) {
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setNodeName(nodeName);
        indexVolume.setPath(path);
        indexVolume.setIndexVolumeGroupId(indexVolumeGroupId);
        AuditUtil.stamp(() -> "test", indexVolume);
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(() -> "test", indexVolumeGroup);
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }
}
