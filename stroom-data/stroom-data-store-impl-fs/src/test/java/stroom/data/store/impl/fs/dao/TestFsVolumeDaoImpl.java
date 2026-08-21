/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.dao;

import stroom.cache.impl.CacheModule;
import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.data.store.impl.fs.db.FsDataStoreDbConnProvider;
import stroom.data.store.impl.fs.db.FsDataStoreDbModule;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.db.util.JooqUtil;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import com.google.inject.Guice;
import com.google.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeGroup.FS_VOLUME_GROUP;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

class TestFsVolumeDaoImpl {

    @Inject
    private FsVolumeDaoImpl fsVolumeDao;
    @Inject
    private FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    @Inject
    private FsVolumeStateDao volumeStateDao;
    @Inject
    private FsVolumeGroupDao volumeGroupDao;

    @BeforeEach
    void beforeEach() {
        Guice.createInjector(
                        new FsDataStoreDaoModule(),
                        new FsDataStoreDbModule(),
                        new MockSecurityContextModule(),
                        new CacheModule(),
                        new MockMetricsModule(),
                        new DbTestModule())
                .injectMembers(this);
        cleanup();
    }

    private void cleanup() {
        JooqUtil.transaction(fsDataStoreDbConnProvider, context -> {
            JooqUtil.deleteAll(context, FS_VOLUME);
            JooqUtil.deleteAll(context, FS_VOLUME_GROUP);
            JooqUtil.deleteAll(context, FS_VOLUME_STATE);
        });
    }

    @Test
    void testCreate() {
//        final FsVolumeGroup group = createTestGroup("testGroup");
//        final FsVolume volume = createTestVolume(group, "/test/path1", FsVolumeType.STANDARD);
//
//        final FsVolume created = fsVolumeDao.create(volume);
//
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getPath()).isEqualTo("/test/path1");
        assertThat(created.getVolumeType()).isEqualTo(FsVolumeType.STANDARD);
        assertThat(created.getVolumeGroup()).isNotNull();
        assertThat(created.getVolumeGroup().getName()).isEqualTo("testGroup");
    }

    @Test
    void testUpdate() {
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        final FsVolume updated = created.copy()
                .path("/test/updated/path")
                .byteLimit(999999L)
                .build();
        final FsVolume result = fsVolumeDao.update(updated);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(created.getId());
        assertThat(result.getPath()).isEqualTo("/test/updated/path");
        assertThat(result.getByteLimit()).isEqualTo(999999L);
    }

    @Test
    void testDelete() {
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        final int deleteCount = fsVolumeDao.delete(created.getId());

        assertThat(deleteCount).isEqualTo(1);
        assertThat(fsVolumeDao.fetch(created.getId())).isNull();
    }

    @Test
    void testFetch() {
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        final FsVolume fetched = fsVolumeDao.fetch(created.getId());

        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getPath()).isEqualTo("/test/path1");
        assertThat(fetched.getVolumeType()).isEqualTo(FsVolumeType.STANDARD);
    }

    @Test
    void testFind_multipleVolumes() {
        createTestVolume("testGroup", "/test/path1", FsVolumeType.STANDARD);
        createTestVolume("testGroup", "/test/path2", FsVolumeType.S3_V1);
        createTestVolume("testGroup", "/test/path3", FsVolumeType.S3_V2);

        final FindFsVolumeCriteria criteria = new FindFsVolumeCriteria(
                new PageRequest(0, 10),
                null,
                null,
                Selection.selectAll());
        final ResultPage<FsVolume> result = fsVolumeDao.find(criteria);

        assertThat(result).isNotNull();
        assertThat(result.getValues()).hasSize(3);
        assertThat(result.getValues())
                .extracting(FsVolume::getPath)
                .containsExactlyInAnyOrder("/test/path1", "/test/path2", "/test/path3");
        assertThat(result.getValues())
                .extracting(FsVolume::getVolumeType)
                .containsExactlyInAnyOrder(FsVolumeType.STANDARD, FsVolumeType.S3_V1, FsVolumeType.S3_V2);

        // Caching uses same group object
        assertThat(result.getValues().get(0).getVolumeGroup())
                .isSameAs(result.getValues().get(1).getVolumeGroup())
                .isSameAs(result.getValues().get(2).getVolumeGroup());
    }

    @Test
    void testGetAll_multipleVolumes() {
        createTestVolume("group1", "/test/path1", FsVolumeType.STANDARD);
        createTestVolume("group2", "/test/path2", FsVolumeType.S3_V1);

        final List<FsVolume> allVolumes = fsVolumeDao.getAll();

        assertThat(allVolumes).isNotNull();
        assertThat(allVolumes).hasSize(2);
        assertThat(allVolumes)
                .extracting(FsVolume::getPath)
                .containsExactlyInAnyOrder("/test/path1", "/test/path2");

        assertThat(allVolumes.stream()
                .map(FsVolume::getVolumeGroup)
                .distinct().count())
                .isEqualTo(2);
    }

    @Test
    void testGetByGroupName_multipleVolumes() {
        createTestVolume("group1", "/group1/path1", FsVolumeType.STANDARD);
        createTestVolume("group1", "/group1/path2", FsVolumeType.STANDARD);
        createTestVolume("group2", "/group2/path1", FsVolumeType.S3_V1);
        createTestVolume("group2", "/group2/path2", FsVolumeType.S3_V1);
        createTestVolume("group3", "/group3/path1", FsVolumeType.S3_V2);
        createTestVolume("group3", "/group3/path2", FsVolumeType.S3_V2);

        Stream.of("group1", "group2", "group3")
                .forEach(groupName -> {
                    final List<FsVolume> volumes = fsVolumeDao.getVolumesInGroup(groupName);
                    assertThat(volumes).isNotNull();
                    assertThat(volumes).hasSize(2);
                    assertThat(volumes)
                            .extracting(FsVolume::getPath)
                            .containsExactlyInAnyOrder(
                                    "/" + groupName + "/path1",
                                    "/" + groupName + "/path2");
                    assertThat(volumes)
                            .allMatch(v -> groupName.equals(v.getVolumeGroup().getName()));

                });
    }

    @Test
    void testGetVolumeState() {
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        final FsVolumeState state = volumeStateDao.fetch(created.getVolumeState().getId())
                .orElseThrow();

        assertThat(state).isNotNull();
        assertThat(state).isEqualTo(created.getVolumeState());
    }

    @Test
    void testUpdateVolumeState() {
        final FsVolume created = createTestVolume(
                "testGroup", "/test/path1", FsVolumeType.STANDARD);

        final FsVolumeState state = volumeStateDao.fetch(created.getId())
                .orElseThrow();

        final FsVolumeState newState = state.copy()
                .bytesUsed(123456L)
                .bytesFree(654321L)
                .bytesTotal(777777L)
                .build();
        final FsVolumeState updated = volumeStateDao.update(newState);

        final FsVolumeState fetched = volumeStateDao.fetch(created.getId())
                .orElseThrow();
        assertThat(fetched).isNotNull();

        assertThat(fetched.getBytesUsed()).isEqualTo(123456L);
        assertThat(fetched.getBytesFree()).isEqualTo(654321L);
        assertThat(fetched.getBytesTotal()).isEqualTo(777777L);

        assertThat(fetched.getBytesUsed()).isNotEqualTo(state.getBytesUsed());
        assertThat(fetched.getBytesFree()).isNotEqualTo(state.getBytesFree());
        assertThat(fetched.getBytesTotal()).isNotEqualTo(state.getBytesTotal());
    }

    private FsVolume createTestVolume(final String volumeGroupName,
                                      final String path,
                                      final FsVolumeType volumeType) {
        final FsVolumeState newState = FsVolumeState.builder()
                .bytesUsed(0L)
                .bytesFree(1000000L)
                .bytesTotal(1000000L)
                .updateTimeMs(System.currentTimeMillis())
                .build();
        return createTestVolume(volumeGroupName, path, volumeType, newState);
    }

    private FsVolume createTestVolume(final String volumeGroupName,
                                      final String path,
                                      final FsVolumeType volumeType,
                                      final FsVolumeState volumeState) {
        final FsVolumeState createdVolumeState = volumeStateDao.create(volumeState);

        final FsVolumeGroup group = volumeGroupDao.getOrCreate(FsVolumeGroup.builder()
                .name(volumeGroupName)
                .stampAudit("joe bloggs")
                .build());

        final FsVolume fsVolume = FsVolume.builder()
                .volumeGroup(group)
                .volumeState(createdVolumeState)
                .path(path)
                .volumeType(volumeType)
                .status(VolumeUseStatus.ACTIVE)
                .byteLimit(1000000L)
                .stampAudit("joe bloggs")
                .build();

        return fsVolumeDao.create(fsVolume);
    }
}
