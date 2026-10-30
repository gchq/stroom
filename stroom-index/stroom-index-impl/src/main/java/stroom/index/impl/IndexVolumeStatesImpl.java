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
import stroom.index.api.IndexVolumeStates;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

class IndexVolumeStatesImpl implements IndexVolumeStates {

    private final IndexVolumeDao indexVolumeDao;
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexVolumeGroupService indexVolumeGroupService;

    @Inject
    IndexVolumeStatesImpl(final IndexVolumeDao indexVolumeDao,
                          final IndexVolumeGroupDao indexVolumeGroupDao,
                          final IndexVolumeGroupService indexVolumeGroupService) {
        this.indexVolumeDao = indexVolumeDao;
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeGroupService = indexVolumeGroupService;
    }

    @Override
    public Map<String, List<IndexVolume>> volumesByGroup() {
        final Map<Integer, String> groupNames = indexVolumeGroupDao.getAll().stream()
                .collect(Collectors.toMap(IndexVolumeGroup::getId, IndexVolumeGroup::getName));
        final Map<String, List<IndexVolume>> byGroup = new TreeMap<>();
        groupNames.values().forEach(name -> byGroup.put(name, new ArrayList<>()));
        indexVolumeDao.getAll().stream()
                .sorted(Comparator.comparing(IndexVolume::getNodeName, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(IndexVolume::getPath, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(volume -> byGroup.computeIfAbsent(
                        groupNames.getOrDefault(volume.getIndexVolumeGroupId(), ""),
                        ignored -> new ArrayList<>()).add(volume));
        return byGroup;
    }

    @Override
    public Optional<String> defaultGroup() {
        return indexVolumeGroupService.getDefaultVolumeGroup();
    }
}
