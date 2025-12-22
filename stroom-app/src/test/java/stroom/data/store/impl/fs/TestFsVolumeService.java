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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.ByteSizeUnit;
import stroom.util.io.PathCreator;
import stroom.util.io.capacity.RoundRobinCapacitySelector;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestFsVolumeService extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFsVolumeService.class);

    @Inject
    private FsVolumeService fsVolumeService;

    @Inject
    private Provider<FsVolumeConfig> volumeConfigProvider;

    @Inject
    private PathCreator pathCreator;

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    @Test
    void testDefaultVolumesPresence() {

        Assertions.assertThat(volumeConfigProvider.get().isCreateDefaultStreamVolumesOnStart())
                .isTrue();

        final List<String> defaultStreamVolumePaths = volumeConfigProvider.get()
                .getDefaultStreamVolumePaths();

        // Make sure we have a vol
        final FsVolume volume = fsVolumeService.getVolume(null);
        Assertions.assertThat(volume)
                .isNotNull();

        Assertions.assertThat(volume.getPath())
                .isIn(defaultStreamVolumePaths);
    }

    @Test
    void testVolumeSelection() {
        setConfigValueMapper(FsVolumeConfig.class, fsVolumeConfig ->
                fsVolumeConfig.withVolumeSelector(RoundRobinCapacitySelector.NAME));

        // Delete any default vols first
        FsVolume fsVolume;
        do {
            fsVolume = fsVolumeService.getVolume(null);
            if (fsVolume != null) {
                fsVolumeService.delete(fsVolume.getId());
            }
        } while (fsVolume != null);

        final List<String> paths = new ArrayList<>();
        final List<FsVolume> fsVolumes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final String path = pathCreator.toAppPath("testFsVol_" + i).toString();
            paths.add(path);
            fsVolume = new FsVolume();
            fsVolume.setPath(path);

            final FsVolumeState fsVolumeState = new FsVolumeState();
            fsVolumeState.setBytesUsed(ByteSizeUnit.GIBIBYTE.longBytes(1));
            fsVolumeState.setBytesFree(ByteSizeUnit.GIBIBYTE.longBytes(4));
            fsVolumeState.setBytesTotal(ByteSizeUnit.GIBIBYTE.longBytes(5));
            fsVolumeState.setUpdateTimeMs(Instant.now().toEpochMilli());
            fsVolume.setVolumeState(fsVolumeState);

            final FsVolume dbFsVolume = fsVolumeService.create(fsVolume);
            fsVolumes.add(dbFsVolume);
        }

        // Close the last vol so it should not be selected
        fsVolumes.get(4).setStatus(VolumeUseStatus.CLOSED);
        fsVolumes.set(4, fsVolumeService.update(fsVolumes.get(4)));

        // Make sure all paths are there
        final ResultPage<FsVolume> fsVolumeResultPage = fsVolumeService.find(FindFsVolumeCriteria.matchAll());

        Assertions.assertThat(fsVolumeResultPage.getValues())
                .hasSize(5);
        Assertions.assertThat(fsVolumeResultPage.getValues().get(4).getStatus())
                .isEqualTo(VolumeUseStatus.CLOSED);

        Assertions.assertThat(fsVolumeResultPage.getValues()
                        .stream()
                        .map(FsVolume::getPath)
                        .collect(Collectors.toList()))
                .containsExactlyElementsOf(paths);

        // Now test vol selection
        for (int i = 0; i < 5; i++) {
            fsVolume = fsVolumeService.getVolume(null);
            Assertions.assertThat(fsVolume)
                    .isNotNull();
            LOGGER.info("Path: " + fsVolume.getPath());

            // 4 is not active so it should not be selected, so it loops back to vol zero
            final String expectedPath = i == 4
                    ? paths.get(0)
                    : paths.get(i);

            Assertions.assertThat(fsVolume.getPath())
                    .isEqualTo(expectedPath);
        }
    }
}
