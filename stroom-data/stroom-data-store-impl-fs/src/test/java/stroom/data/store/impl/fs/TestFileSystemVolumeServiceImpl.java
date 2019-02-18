/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.store.impl.fs;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FSVolumeState;
import stroom.data.store.impl.fs.shared.FindFSVolumeCriteria;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.impl.SecurityImpl;
import stroom.security.impl.mock.MockSecurityContext;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestFileSystemVolumeServiceImpl extends StroomUnitTest {
    private static final Path DEFAULT_VOLUMES_PATH;
    private static final Path DEFAULT_STREAM_VOLUME_PATH;

    static {
        DEFAULT_VOLUMES_PATH = FileUtil.getTempDir().resolve(FileSystemVolumeServiceImpl.DEFAULT_VOLUMES_SUBDIR);
        DEFAULT_STREAM_VOLUME_PATH = DEFAULT_VOLUMES_PATH.resolve(FileSystemVolumeServiceImpl.DEFAULT_STREAM_VOLUME_SUBDIR);
    }

    private final FSVolume public1a = FSVolume.create(
            FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1A")),
            FSVolumeState.create(0, 1000));
    private final FSVolume public1b = FSVolume.create(
            FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_1A")),
            FSVolumeState.create(0, 1000));
    private final FSVolume public2a = FSVolume.create(
            FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2A")),
            FSVolumeState.create(0, 1000));
    private final FSVolume public2b = FSVolume.create(
            FileUtil.getCanonicalPath(FileUtil.getTempDir().resolve("PUBLIC_2B")),
            FSVolumeState.create(0, 1000));
    private final Security security = new SecurityImpl(new MockSecurityContext());
    private FileSystemVolumeConfig volumeConfig = new FileSystemVolumeConfig();
    private FileSystemVolumeService volumeService = null;

    @BeforeEach
    void init() {
        deleteDefaultVolumesDir();

//        final List<FileVolume> volumeList = new ArrayList<>();
//        volumeList.add(public1a);
//        volumeList.add(public1b);
//        volumeList.add(public2a);
//        volumeList.add(public2b);

        final SecurityContext securityContext = new MockSecurityContext();
        final Security security = new SecurityImpl(securityContext);

        final ConnectionProvider connectionProvider = new FileSystemDataStoreModule().getConnectionProvider(DataStoreServiceConfig::new);
        volumeService = new FileSystemVolumeServiceImpl(connectionProvider,
                security,
                securityContext,
                new FileSystemVolumeConfig(),
                Optional.empty(),
                new FileSystemVolumeStateDao(connectionProvider),
                null,
                null);

//        volumeService.volumeList = volumeList;
    }

    @Test
    void test() {
        List<FSVolume> list = volumeService.find(new FindFSVolumeCriteria());
        list.forEach(v -> volumeService.delete(v.getId()));

        list = volumeService.find(new FindFSVolumeCriteria());
        assertThat(list.size()).isZero();

        // Create
        FSVolume fileVolume = volumeService.create(public1a);
        fileVolume.setByteLimit(2000000L);

        // Update
        fileVolume = volumeService.update(fileVolume);

        // Find
        list = volumeService.find(new FindFSVolumeCriteria());
        assertThat(list.size()).isOne();
        assertThat(list.get(0)).isEqualTo(fileVolume);

        // Fetch
        assertThat(volumeService.fetch(fileVolume.getId())).isEqualTo(fileVolume);

        // Delete
        final int count = volumeService.delete(fileVolume.getId());
        assertThat(count).isOne();
        list = volumeService.find(new FindFSVolumeCriteria());
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
//                .map(FileVolume::getPath)
//                .filter(path -> path.equals(FileUtil.getCanonicalPath(DEFAULT_INDEX_VOLUME_PATH)) ||
//                        path.equals(FileUtil.getCanonicalPath(DEFAULT_STREAM_VOLUME_PATH)))
//                .count()).isEqualTo(2);
//        assertThat(Files.exists(DEFAULT_INDEX_VOLUME_PATH)).isTrue();
//        assertThat(Files.exists(DEFAULT_STREAM_VOLUME_PATH)).isTrue();
//    }

    private void deleteDefaultVolumesDir() {
        FileUtil.deleteDir(DEFAULT_STREAM_VOLUME_PATH);
        FileUtil.deleteDir(DEFAULT_VOLUMES_PATH);
    }
}
