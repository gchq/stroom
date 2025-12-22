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

package stroom.index.mock;

import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockIndexVolumeGroupService implements IndexVolumeGroupService {

    private final List<IndexVolumeGroup> groups = new ArrayList<>();
    private static final String TEST_USER = "testVolumeGroupUser";

    @Override
    public List<String> getNames() {
        return groups.stream()
                .map(IndexVolumeGroup::getName)
                .toList();
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return groups;
    }

    @Override
    public IndexVolumeGroup create() {
        final IndexVolumeGroup group = new IndexVolumeGroup();
        group.setName("New name");
        AuditUtil.stamp(() -> TEST_USER, group);
        groups.add(group);
        return group;
    }

    @Override
    public IndexVolumeGroup getOrCreate(final String name) {
        final IndexVolumeGroup group = new IndexVolumeGroup();
        group.setName(name);
        AuditUtil.stamp(() -> TEST_USER, group);
        groups.add(group);
        return group;
    }

    @Override
    public IndexVolumeGroup update(final IndexVolumeGroup indexVolumeGroup) {
        return null;
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return null;
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return groups.stream()
                .filter(g -> id == g.getId())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void delete(final int id) {
        groups.removeIf(i -> id == i.getId());
    }

    @Override
    public void ensureDefaultVolumes() {

    }

    @Override
    public Optional<String> getDefaultVolumeGroup() {
        return Optional.empty();
    }
}
