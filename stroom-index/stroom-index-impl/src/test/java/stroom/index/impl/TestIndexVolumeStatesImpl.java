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

package stroom.index.impl;

import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeStatesImpl {

    private final IndexVolumeDao indexVolumeDao = Mockito.mock(IndexVolumeDao.class);
    private final IndexVolumeGroupDao indexVolumeGroupDao = Mockito.mock(IndexVolumeGroupDao.class);
    private final IndexVolumeGroupService indexVolumeGroupService = Mockito.mock(IndexVolumeGroupService.class);
    private final IndexVolumeStatesImpl states = new IndexVolumeStatesImpl(indexVolumeDao, indexVolumeGroupDao,
            indexVolumeGroupService);

    @Test
    void eachGroupIsGivenInNameOrderWithItsVolumesByNodeAndPath() {
        Mockito.when(indexVolumeGroupDao.getAll()).thenReturn(List.of(group(1, "Group1"), group(2, "Empty")));
        final IndexVolume node2 = volume("node2", "/idx/a", 1);
        final IndexVolume node1b = volume("node1", "/idx/b", 1);
        final IndexVolume node1a = volume("node1", "/idx/a", 1);
        Mockito.when(indexVolumeDao.getAll()).thenReturn(List.of(node2, node1b, node1a));

        final Map<String, List<IndexVolume>> byGroup = states.volumesByGroup();

        assertThat(byGroup.keySet()).containsExactly("Empty", "Group1");
        assertThat(byGroup.get("Group1")).containsExactly(node1a, node1b, node2);
        assertThat(byGroup.get("Empty")).isEmpty();
    }

    @Test
    void theDefaultGroupIsTheConfiguredOne() {
        Mockito.when(indexVolumeGroupService.getDefaultVolumeGroup()).thenReturn(Optional.of("Group1"));

        assertThat(states.defaultGroup()).contains("Group1");
    }

    private static IndexVolumeGroup group(final int id, final String name) {
        return IndexVolumeGroup.builder().id(id).name(name).build();
    }

    private static IndexVolume volume(final String node, final String path, final int groupId) {
        return IndexVolume.builder().nodeName(node).path(path).indexVolumeGroupId(groupId).build();
    }
}
