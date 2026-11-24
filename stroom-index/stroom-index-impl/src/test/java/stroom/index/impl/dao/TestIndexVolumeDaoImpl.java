/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.impl.dao;

import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.IndexVolumeGroup;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeDaoImpl {

    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;
    private static IndexShardDao indexShardDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
        indexShardDao = injector.getInstance(IndexShardDao.class);
    }

    @Test
    void testNewVolumeIsLocal() {
        // A volume created without a type is local, as every volume was before volumes had a type.
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());

        final IndexVolume created = createVolume(indexVolumeGroup.getId());

        assertThat(indexVolumeDao.fetch(created.getId()))
                .get()
                .extracting(IndexVolume::getVolumeType)
                .isEqualTo(VolumeType.LOCAL);
    }

    @Test
    void testSharedVolumeIsUsableByEveryNode() {
        final String groupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(groupName);
        final String node1 = TestData.createNodeName();
        final String node2 = TestData.createNodeName();
        final IndexVolume local1 = createVolume(node1, TestData.createPath(), indexVolumeGroup.getId());
        final IndexVolume local2 = createVolume(node2, TestData.createPath(), indexVolumeGroup.getId());
        final IndexVolume shared = createSharedVolume(TestData.createPath(), indexVolumeGroup.getId());

        assertThat(shared.getVolumeType()).isEqualTo(VolumeType.SHARED);
        assertThat(shared.getNodeName()).isNull();

        // Each node gets its own local volume and the shared one, but not the other node's local volume.
        assertThat(indexVolumeDao.getVolumesInGroupOnNode(groupName, node1))
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(local1.getId(), shared.getId());
        assertThat(indexVolumeDao.getVolumesInGroupOnNode(groupName, node2))
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(local2.getId(), shared.getId());
        assertThat(indexVolumeDao.getVolumesOnNodeGrouped(node1).get(groupName))
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(local1.getId(), shared.getId());

        // A node with no volumes of its own still gets the shared one.
        assertThat(indexVolumeDao.getVolumesInGroupOnNode(groupName, TestData.createNodeName()))
                .extracting(IndexVolume::getId)
                .containsExactly(shared.getId());
    }

    @Test
    void testMergeIntoSharedVolume() {
        // Given three nodes that each have a volume row for the same path, and a shard on each row
        final String groupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(groupName);
        final String path = TestData.createPath();
        final String node1 = TestData.createNodeName();
        final String node2 = TestData.createNodeName();
        final String node3 = TestData.createNodeName();
        final IndexVolume volume1 = createVolume(node1, path, indexVolumeGroup.getId());
        final IndexVolume volume2 = createVolume(node2, path, indexVolumeGroup.getId());
        final IndexVolume volume3 = createVolume(node3, path, indexVolumeGroup.getId());
        // A volume with another path is not part of the merge.
        final IndexVolume otherVolume = createVolume(node2, TestData.createPath(), indexVolumeGroup.getId());

        final IndexShard shard1 = createShard(volume1, node1);
        final IndexShard shard2 = createShard(volume2, node2);
        final IndexShard shard3 = createShard(volume3, node3);
        final IndexShard otherShard = createShard(otherVolume, node2);

        // When the first node's volume is marked as shared and the other two are merged into it
        final IndexVolume shared = indexVolumeDao.mergeIntoSharedVolume(
                volume1.copy()
                        .nodeName(null)
                        .volumeType(VolumeType.SHARED)
                        .build(),
                List.of(volume2.getId(), volume3.getId()));

        // Then one shared row is left for the path
        assertThat(shared.getId()).isEqualTo(volume1.getId());
        assertThat(shared.getVolumeType()).isEqualTo(VolumeType.SHARED);
        assertThat(shared.getNodeName()).isNull();
        assertThat(indexVolumeDao.fetch(volume2.getId())).isEmpty();
        assertThat(indexVolumeDao.fetch(volume3.getId())).isEmpty();
        assertThat(indexVolumeDao.getVolumesInGroup(groupName))
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(shared.getId(), otherVolume.getId());

        // And every shard points at it, still owned by the node that wrote it
        for (final IndexShard shard : List.of(shard1, shard2, shard3)) {
            final IndexShard reloaded = indexShardDao.fetch(shard.getId()).orElseThrow();
            assertThat(reloaded.getVolume().getId()).isEqualTo(shared.getId());
            assertThat(reloaded.getVolume().getVolumeType()).isEqualTo(VolumeType.SHARED);
            assertThat(reloaded.getNodeName()).isEqualTo(shard.getNodeName());
        }
        assertThat(indexShardDao.fetch(otherShard.getId()).orElseThrow().getVolume().getId())
                .isEqualTo(otherVolume.getId());
    }

    @Test
    void testMergeIntoSharedVolume_nothingToMerge() {
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final IndexVolume volume = createVolume(indexVolumeGroup.getId());
        final IndexShard shard = createShard(volume, volume.getNodeName());

        final IndexVolume shared = indexVolumeDao.mergeIntoSharedVolume(
                volume.copy()
                        .nodeName(null)
                        .volumeType(VolumeType.SHARED)
                        .build(),
                List.of());

        assertThat(shared.getVolumeType()).isEqualTo(VolumeType.SHARED);
        assertThat(indexShardDao.fetch(shard.getId()).orElseThrow().getVolume().getId())
                .isEqualTo(shared.getId());
    }

    @Test
    void testCreate() {
        // Given
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());

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
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());

        final String newNodeName = TestData.createNodeName();
        final String newPath = TestData.createPath();

        final IndexVolume indexVolume = createVolume(nodeName, path, indexVolumeGroup.getId())
                .copy()
                .nodeName(newNodeName)
                .path(newPath)
                .build();

        // When
        final IndexVolume updatedIndexVolume = indexVolumeDao.update(indexVolume);

        // Then
        assertThat(updatedIndexVolume.getNodeName()).isEqualTo(newNodeName);
        assertThat(updatedIndexVolume.getPath()).isEqualTo(newPath);
    }

    @Test
    void testDelete() {
        // Given
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final IndexVolume indexVolume = createVolume(nodeName, path, indexVolumeGroup.getId());

        // When
        indexVolumeDao.delete(indexVolume.getId());

        // Then
        final Optional<IndexVolume> deletedVolumeOptional = indexVolumeDao.fetch(indexVolume.getId());

        assertThat(deletedVolumeOptional.isPresent()).isFalse();
    }

    @Test
    void testGetAll() {
        // Given
        final IndexVolumeGroup indexVolumeGroup01 = createGroup(TestData.createVolumeGroupName());
        final IndexVolumeGroup indexVolumeGroup02 = createGroup(TestData.createVolumeGroupName());
        final IndexVolumeGroup indexVolumeGroup03 = createGroup(TestData.createVolumeGroupName());
        createVolume(indexVolumeGroup01.getId());
        createVolume(indexVolumeGroup01.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup02.getId());
        createVolume(indexVolumeGroup03.getId());

        // When
        final List<IndexVolume> indexVolumes = indexVolumeDao.getAll();

        // Then
        final BiConsumer<Integer, Integer> checkTheNewVolumeExists = (id, expectedCount) -> {
            final List<IndexVolume> foundIndexVolumesForGroup = indexVolumes.stream()
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
        final IndexVolumeGroup indexVolumeGroup01 = createGroup(TestData.createVolumeGroupName());
        final IndexVolume indexVolume = createVolume(indexVolumeGroup01.getId())
                .copy().indexVolumeGroupId(null).build();

        // When / then
        Assertions.assertThatThrownBy(() -> indexVolumeDao.update(indexVolume))
                .isInstanceOf(DataAccessException.class);
    }

    private IndexVolume createVolume(final int indexVolumeGroupId) {
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        return createVolume(nodeName, path, indexVolumeGroupId);
    }

    private IndexVolume createVolume(final String nodeName, final String path, final int indexVolumeGroupId) {
        final IndexVolume indexVolume = IndexVolume
                .builder()
                .nodeName(nodeName)
                .path(path)
                .indexVolumeGroupId(indexVolumeGroupId)
                .stampAudit("test")
                .build();
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolume createSharedVolume(final String path, final int indexVolumeGroupId) {
        final IndexVolume indexVolume = IndexVolume
                .builder()
                .path(path)
                .volumeType(VolumeType.SHARED)
                .indexVolumeGroupId(indexVolumeGroupId)
                .stampAudit("test")
                .build();
        return indexVolumeDao.create(indexVolume);
    }

    private IndexShard createShard(final IndexVolume indexVolume, final String ownerNodeName) {
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(UUID.randomUUID().toString())
                .partition(AllPartition.INSTANCE)
                .build();
        return indexShardDao.create(indexShardKey, indexVolume, ownerNodeName, "1.0-test");
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = IndexVolumeGroup
                .builder()
                .name(name)
                .stampAudit("test")
                .build();
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }
}
