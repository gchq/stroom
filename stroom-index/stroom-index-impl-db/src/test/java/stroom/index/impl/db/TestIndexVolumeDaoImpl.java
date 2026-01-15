/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.impl.db;

import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
        final IndexVolume indexVolume = createVolume(nodeName, path, indexVolumeGroup.getId());

        final String newNodeName = TestData.createNodeName();
        final String newPath = TestData.createPath();

        indexVolume.setNodeName(newNodeName);
        indexVolume.setPath(newPath);

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
        final IndexVolume indexVolume = createVolume(indexVolumeGroup01.getId());

        // When / then
        indexVolume.setIndexVolumeGroupId(null);
        Assertions.assertThatThrownBy(() -> indexVolumeDao.update(indexVolume))
                .isInstanceOf(DataAccessException.class);
    }

    private IndexVolume createVolume(final int indexVolumeGroupId) {
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
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
