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

package stroom.data.store.impl.fs.db;


import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.DataStoreServiceConfig.DataStoreServiceDbConfig;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.FsVolumeGroupServiceImpl;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.node.api.NodeInfo;
import stroom.node.mock.MockNodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.capacity.HasCapacitySelectorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestFileSystemVolumeServiceImpl extends StroomUnitTest {

    //    private static final Path DEFAULT_VOLUMES_PATH;
//    private static final Path DEFAULT_STREAM_VOLUME_PATH;
//
//    static {
//        DEFAULT_VOLUMES_PATH = tempDir.resolve("volumes");
//        DEFAULT_STREAM_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve("defaultStreamVolume");
//    }
//
//    private final FsVolume public1a = FsVolume.create(
//            FileUtil.getCanonicalPath(tempDir.resolve("PUBLIC_1A")),
//            FsVolumeState.create(0, 1000));
//    private final FsVolume public1b = FsVolume.create(
//            FileUtil.getCanonicalPath(tempDir.resolve("PUBLIC_1A")),
//            FsVolumeState.create(0, 1000));
//    private final FsVolume public2a = FsVolume.create(
//            FileUtil.getCanonicalPath(tempDir.resolve("PUBLIC_2A")),
//            FsVolumeState.create(0, 1000));
//    private final FsVolume public2b = FsVolume.create(
//            FileUtil.getCanonicalPath(tempDir.resolve("PUBLIC_2B")),
//            FsVolumeState.create(0, 1000));
//    //    private final SecurityContext securityContext = new MockSecurityContext();
////    private FileSystemVolumeConfig volumeConfig = new FileSystemVolumeConfig();
    private FsVolumeService volumeService = null;

    @TempDir
    static Path tempDir;

    @BeforeEach
    void init() {
//        deleteDefaultVolumesDir();

//        final List<FileVolume> volumeList = new ArrayList<>();
//        volumeList.add(public1a);
//        volumeList.add(public1b);
//        volumeList.add(public2a);
//        volumeList.add(public2b);

        final SecurityContext securityContext = new MockSecurityContext();
        final NodeInfo nodeInfo = new MockNodeInfo();

        final FsDataStoreDbConnProvider fsDataStoreDbConnProvider = DbTestUtil.getTestDbDatasource(
                new FsDataStoreDbModule(), new DataStoreServiceDbConfig());

        final FsVolumeDao fsVolumeDao = new FsVolumeDaoImpl(fsDataStoreDbConnProvider);
        final FsVolumeGroupDao fsVolumeGroupDao = new FsVolumeGroupDaoImpl(fsDataStoreDbConnProvider);
        final FsVolumeGroupService fsVolumeGroupService = new FsVolumeGroupServiceImpl(
                fsVolumeGroupDao,
                securityContext,
                FsVolumeConfig::new,
                null);
        final FsVolumeStateDao fsVolumeStateDao = new FsVolumeStateDaoImpl(fsDataStoreDbConnProvider);
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        volumeService = new FsVolumeService(
                fsVolumeDao,
                fsVolumeGroupService,
                fsVolumeStateDao,
                securityContext,
                FsVolumeConfig::new,
                null,
                null,
                null,
                pathCreator,
                nodeInfo,
                new SimpleTaskContextFactory(),
                new HasCapacitySelectorFactory());

//        volumeService.volumeList = volumeList;
    }

    @Test
    void test() {
        List<FsVolume> list = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        list.forEach(v -> volumeService.delete(v.getId()));

        list = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        assertThat(list.size()).isZero();

        // Create
        final FsVolume public1a = FsVolume.create(
                FileUtil.getCanonicalPath(tempDir.resolve("PUBLIC_1A")),
                FsVolumeState.create(0, 1000));
        FsVolume fileVolume = volumeService.create(public1a);
        fileVolume.setByteLimit(2000000L);

        // Update
        fileVolume = volumeService.update(fileVolume);

        // Find
        list = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        assertThat(list.size()).isOne();
        assertThat(list.get(0)).isEqualTo(fileVolume);

        // Fetch
        assertThat(volumeService.fetch(fileVolume.getId())).isEqualTo(fileVolume);

        // Delete
        final int count = volumeService.delete(fileVolume.getId());
        assertThat(count).isOne();
        list = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        assertThat(list.size()).isZero();
    }


//    @Test
//    void testNode1aNodeWithCacheAndSomePuckerLocalStorage() {
//        final Set<FileVolume> call1 = volumeService.getStreamVolumeSet(node1a);
//        final Set<FileVolume> call2 = volumeService.getStreamVolumeSet(node1a);
//        assertThat(call1.size()).isEqualTo(2);
//        assertThat(call2.size()).isEqualTo(2);
//
//        // Check that we only write once in a rack
//        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
//        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
//        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
//        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();
//
//        // Check that we round robin OK
//        assertThat(call1.contains(public2a) ^ call2.contains(public2a)).isTrue();
//        assertThat(call1.contains(public2b) ^ call2.contains(public2b)).isTrue();
//    }
//
//    @Test
//    void testNode1cNodeWithNoStorage() {
//        final Set<FileVolume> call1 = volumeService.getStreamVolumeSet(node1c);
//        final Set<FileVolume> call2 = volumeService.getStreamVolumeSet(node1c);
//        assertThat(call1.size()).isEqualTo(2);
//        assertThat(call2.size()).isEqualTo(2);
//
//        // Check that we only write once in a rack
//        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
//        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
//        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
//        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();
//
//        // Check that we round robin OK
//        assertThat(call1.contains(public1a) ^ call2.contains(public1a)).isTrue();
//        assertThat(call1.contains(public1b) ^ call2.contains(public1b)).isTrue();
//        assertThat(call1.contains(public2a) ^ call2.contains(public2a)).isTrue();
//        assertThat(call1.contains(public2b) ^ call2.contains(public2b)).isTrue();
//    }
//
//    @Test
//    void testNode2aNodeWithNoCache() {
//        final Set<FileVolume> call1 = volumeService.getStreamVolumeSet(node2a);
//        final Set<FileVolume> call2 = volumeService.getStreamVolumeSet(node2a);
//        assertThat(call1.size()).isEqualTo(2);
//        assertThat(call2.size()).isEqualTo(2);
//
//        // Check that we only write once in a rack
//        assertThat(call1.contains(public1a) ^ call1.contains(public1b)).isTrue();
//        assertThat(call1.contains(public2a) ^ call1.contains(public2b)).isTrue();
//        assertThat(call2.contains(public1a) ^ call2.contains(public1b)).isTrue();
//        assertThat(call2.contains(public2a) ^ call2.contains(public2b)).isTrue();
//
//        // Check that we round robin OK on rack 1
//        assertThat(call1.contains(public1a) ^ call2.contains(public1a)).isTrue();
//        assertThat(call1.contains(public1b) ^ call2.contains(public1b)).isTrue();
//    }
//
//    @Test
//    void testStartup_Disabled() {
//        volumeConfig.setCreateDefaultOnStart(false);
//
//        assertThat(volumeService.saveCalled).isFalse();
//        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isFalse();
//        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isFalse();
//    }
//
//    @Test
//    void testStartup_EnabledExistingVolumes() {
//        volumeConfig.setCreateDefaultOnStart(true);
//
//        assertThat(volumeService.saveCalled).isFalse();
//        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isFalse();
//        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isFalse();
//    }
//
//    @Test
//    void testStartup_EnabledNoExistingVolumes() {
//        volumeConfig.setCreateDefaultOnStart(true);
//        volumeService.volumeList.clear();
//        volumeService.getStreamVolumeSet(node1a);
////        volumeService.startup();
//
//        assertThat(volumeService.saveCalled).isTrue();
//        //make sure both paths have been saved
//        assertThat(volumeService.savedVolumes.stream()
//                .map(FileVolume::getOrCreatePath)
//                .filter(path -> path.equals(FileUtil.getCanonicalPath(DEFAULT_INDEX_VOLUME_PATH)) ||
//                        path.equals(FileUtil.getCanonicalPath(DEFAULT_STREAM_VOLUME_PATH)))
//                .count()).isEqualTo(2);
//        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isTrue();
//        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isTrue();
//    }
//
//    private void deleteDefaultVolumesDir() {
//        FileUtil.deleteDir(DEFAULT_STREAM_VOLUME_PATH);
//        FileUtil.deleteDir(DEFAULT_VOLUMES_PATH);
//    }
}
