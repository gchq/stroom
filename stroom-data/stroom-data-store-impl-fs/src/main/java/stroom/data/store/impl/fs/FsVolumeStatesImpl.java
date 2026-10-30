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
import stroom.data.store.api.FsVolumeStates;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

class FsVolumeStatesImpl implements FsVolumeStates {

    private final FsVolumeDao fsVolumeDao;
    private final FsVolumeGroupDao fsVolumeGroupDao;
    private final FsVolumeGroupService fsVolumeGroupService;

    @Inject
    FsVolumeStatesImpl(final FsVolumeDao fsVolumeDao,
                       final FsVolumeGroupDao fsVolumeGroupDao,
                       final FsVolumeGroupService fsVolumeGroupService) {
        this.fsVolumeDao = fsVolumeDao;
        this.fsVolumeGroupDao = fsVolumeGroupDao;
        this.fsVolumeGroupService = fsVolumeGroupService;
    }

    @Override
    public Map<String, List<FsVolume>> volumesByGroup() {
        final Map<String, List<FsVolume>> byGroup = new TreeMap<>();
        fsVolumeGroupDao.getAll().forEach(group -> byGroup.put(group.getName(), new ArrayList<>()));
        fsVolumeDao.getAll().stream()
                .sorted(Comparator.comparing(FsVolume::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(volume -> {
                    final String group = Optional.ofNullable(volume.getVolumeGroup())
                            .map(FsVolumeGroup::getName)
                            .orElse("");
                    byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(volume);
                });
        return byGroup;
    }

    @Override
    public Optional<String> defaultGroup() {
        return fsVolumeGroupService.getDefaultVolumeGroupName();
    }
}
