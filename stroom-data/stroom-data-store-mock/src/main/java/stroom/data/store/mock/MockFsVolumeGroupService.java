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

package stroom.data.store.mock;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;

import java.util.List;
import java.util.Optional;

public class MockFsVolumeGroupService implements FsVolumeGroupService {

    @Override
    public List<String> getNames() {
        return null;
    }

    @Override
    public List<FsVolumeGroup> getAll() {
        return null;
    }

    @Override
    public FsVolumeGroup create() {
        return null;
    }

    @Override
    public FsVolumeGroup getOrCreate(final String name) {
        return null;
    }

    @Override
    public FsVolumeGroup update(final FsVolumeGroup indexVolumeGroup) {
        return null;
    }

    @Override
    public FsVolumeGroup get(final String name) {
        return null;
    }

    @Override
    public FsVolumeGroup get(final int id) {
        return null;
    }

    @Override
    public void delete(final int id) {

    }

    @Override
    public void ensureDefaultVolumes() {

    }

    @Override
    public Optional<String> getDefaultVolumeGroup() {
        return Optional.empty();
    }
}
