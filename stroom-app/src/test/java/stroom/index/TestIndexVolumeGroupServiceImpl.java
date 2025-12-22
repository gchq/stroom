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

package stroom.index;

import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolumeGroup;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.capacity.RoundRobinCapacitySelector;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TestIndexVolumeGroupServiceImpl extends AbstractCoreIntegrationTest {

    @Inject
    private IndexVolumeGroupService indexVolumeGroupService;
    @Inject
    private Provider<VolumeConfig> volumeConfigProvider;

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    @Test
    void testDefaultGroups() {

        Assertions.assertThat(volumeConfigProvider.get().isCreateDefaultIndexVolumesOnStart())
                .isTrue();

        setConfigValueMapper(VolumeConfig.class, volumeConfig ->
                volumeConfig.withVolumeSelector(RoundRobinCapacitySelector.NAME));

        final String defaultIndexVolumeGroupName = volumeConfigProvider.get().getDefaultIndexVolumeGroupName();

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactly(defaultIndexVolumeGroupName);
    }

    @Test
    void testCreateGroup() {
        final List<String> groupNames = new ArrayList<>(indexVolumeGroupService.getNames());

        final String newGroupname = "newGroupName";

        Assertions.assertThat(groupNames)
                .doesNotContain(newGroupname);

        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate(newGroupname);
        final IndexVolumeGroup volumeGroup2 = indexVolumeGroupService.getOrCreate(newGroupname);
        groupNames.add(newGroupname);

        Assertions.assertThat(volumeGroup)
                .isEqualTo(volumeGroup2);

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactlyElementsOf(groupNames);

        indexVolumeGroupService.delete(volumeGroup.getId());
        groupNames.remove(volumeGroup.getName());

        Assertions.assertThat(indexVolumeGroupService.getNames())
                .containsExactlyElementsOf(groupNames);
    }
}
