/*
 * Copyright 2026 Crown Copyright
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

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestFsVolumeStatesImpl {

    private final FsVolumeDao fsVolumeDao = Mockito.mock(FsVolumeDao.class);
    private final FsVolumeGroupDao fsVolumeGroupDao = Mockito.mock(FsVolumeGroupDao.class);
    private final FsVolumeGroupService fsVolumeGroupService = Mockito.mock(FsVolumeGroupService.class);
    private final FsVolumeStatesImpl states = new FsVolumeStatesImpl(fsVolumeDao, fsVolumeGroupDao,
            fsVolumeGroupService);

    @Test
    void eachGroupIsGivenInNameOrderWithItsVolumesEvenWhenItHasNone() {
        final FsVolumeGroup standard = FsVolumeGroup.builder().name("Standard").build();
        final FsVolumeGroup archive = FsVolumeGroup.builder().name("Archive").build();
        final FsVolumeGroup empty = FsVolumeGroup.builder().name("Empty").build();
        Mockito.when(fsVolumeGroupDao.getAll()).thenReturn(List.of(standard, archive, empty));
        final FsVolume second = FsVolume.builder().id(2).path("/data/2").volumeGroup(standard).build();
        final FsVolume first = FsVolume.builder().id(1).path("/data/1").volumeGroup(standard).build();
        final FsVolume old = FsVolume.builder().id(3).path("/archive/1").volumeGroup(archive).build();
        Mockito.when(fsVolumeDao.getAll()).thenReturn(List.of(second, old, first));

        final Map<String, List<FsVolume>> byGroup = states.volumesByGroup();

        assertThat(byGroup).containsOnlyKeys("Archive", "Empty", "Standard");
        assertThat(byGroup.keySet()).containsExactly("Archive", "Empty", "Standard");
        assertThat(byGroup.get("Standard")).containsExactly(first, second);
        assertThat(byGroup.get("Archive")).containsExactly(old);
        assertThat(byGroup.get("Empty")).isEmpty();
    }

    @Test
    void theDefaultGroupIsTheConfiguredOne() {
        Mockito.when(fsVolumeGroupService.getDefaultVolumeGroupName()).thenReturn(Optional.of("Standard"));

        assertThat(states.defaultGroup()).contains("Standard");
    }
}
