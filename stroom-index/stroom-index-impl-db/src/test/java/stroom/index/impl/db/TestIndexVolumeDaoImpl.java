package stroom.index.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeDaoImpl {
    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new IndexDbModule(), new TestModule());

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
    }

    @Test
    void testCreate() {
        // Given
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();

        // When
        final IndexVolume created = createVolume(nodeName, path);
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
    void testSimpleGroupMembership() {
        // Given
        final String groupName = TestData.createVolumeGroupName();
        final String nodeName = TestData.createNodeName();
        final Set<String> pathsInsideGroup = IntStream.range(0, 10)
                .mapToObj(TestData::createPath)
                .collect(Collectors.toSet());
        final Set<String> pathsOutsideGroup = IntStream.range(0, 10)
                .mapToObj(TestData::createPath)
                .collect(Collectors.toSet()); // used to ensure some 'noise' exists in the db.

        // When
        final IndexVolumeGroup group = createGroup(groupName);
        final Set<IndexVolume> indexVolumesInsideGroup = pathsInsideGroup.stream()
                .map(p -> createVolume(nodeName, p))
                .collect(Collectors.toSet());
        pathsOutsideGroup.forEach(p -> createVolume(nodeName, p)); // noise
        indexVolumesInsideGroup.forEach(i -> indexVolumeDao.addVolumeToGroup(i.getId(), groupName));
        final List<IndexVolume> volumesInGroup = indexVolumeDao.getVolumesInGroup(groupName);

        // Then
        assertThat(group.getName()).isEqualTo(groupName);
        assertThat(volumesInGroup.stream().map(IndexVolume::getId))
                .containsOnlyElementsOf(indexVolumesInsideGroup.stream()
                        .map(IndexVolume::getId)
                        .collect(Collectors.toSet()));
        assertThat(volumesInGroup.stream().map(IndexVolume::getPath))
                .containsOnlyElementsOf(pathsInsideGroup);

    }

    @Test
    void testGroupMembershipRemove() {
        // Given
        final String groupName = TestData.createVolumeGroupName();
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();

        // When
        final IndexVolumeGroup group = createGroup(groupName);
        final IndexVolume indexVolume = createVolume(nodeName, path);
        indexVolumeDao.addVolumeToGroup(indexVolume.getId(), groupName);
        final List<IndexVolume> found1 = indexVolumeDao.getVolumesInGroup(groupName);
        indexVolumeDao.removeVolumeFromGroup(indexVolume.getId(), groupName);
        final List<IndexVolume> found2 = indexVolumeDao.getVolumesInGroup(groupName);

        // Then
        assertThat(group.getName()).isEqualTo(groupName);
        assertThat(found1).hasSize(1);
        assertThat(found1.get(0).getNodeName()).isEqualTo(nodeName);
        assertThat(found1.get(0).getPath()).isEqualTo(path);
        assertThat(found2).hasSize(0);
    }

    @Test
    void testGroupMembershipClear() {
        // Given
        final Set<String> groupNames = IntStream.range(0, 5)
                .mapToObj(TestData::createVolumeGroupName)
                .collect(Collectors.toSet());
        final String nodeName = TestData.createNodeName();
        final String pathToRemove = TestData.createPath();
        final String pathToRemain = TestData.createPath();

        // When
        final IndexVolume volumeToRemove = createVolume(nodeName, pathToRemove);
        final IndexVolume volumeToRemain = createVolume(nodeName, pathToRemain);
        final Set<IndexVolumeGroup> groups = groupNames.stream()
                .map(this::createGroup)
                .peek(g -> indexVolumeDao.addVolumeToGroup(volumeToRemove.getId(), g.getName()))
                .peek(g -> indexVolumeDao.addVolumeToGroup(volumeToRemain.getId(), g.getName()))
                .collect(Collectors.toSet());
        final List<IndexVolume> indexesInGroup1 = indexVolumeDao.getVolumesInGroup(groupNames.iterator().next());
        indexVolumeDao.clearVolumeGroupMemberships(volumeToRemove.getId());
        final List<IndexVolume> indexesInGroup2 = indexVolumeDao.getVolumesInGroup(groupNames.iterator().next());

        // Then
        assertThat(indexesInGroup1.stream().map(IndexVolume::getPath)).containsOnly(pathToRemain, pathToRemove);
        assertThat(indexesInGroup2.stream().map(IndexVolume::getPath)).containsOnly(pathToRemain);

    }

    private IndexVolume createVolume(final String nodeName, final String path) {
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setNodeName(nodeName);
        indexVolume.setPath(path);
        AuditUtil.stamp("test", indexVolume);
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp("test", indexVolumeGroup);
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }
}
