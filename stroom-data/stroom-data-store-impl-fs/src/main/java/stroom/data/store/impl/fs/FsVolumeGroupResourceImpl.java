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

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class FsVolumeGroupResourceImpl implements FsVolumeGroupResource {

    private final Provider<FsVolumeGroupService> volumeGroupServiceProvider;

    @Inject
    FsVolumeGroupResourceImpl(final Provider<FsVolumeGroupService> volumeGroupServiceProvider) {
        this.volumeGroupServiceProvider = volumeGroupServiceProvider;
    }

    @Override
    public ResultPage<FsVolumeGroup> find(final ExpressionCriteria request) {
        return ResultPage.createUnboundedList(volumeGroupServiceProvider.get().getAll());
    }

    @Override
    public FsVolumeGroup create(final String name) {
        return volumeGroupServiceProvider.get().getOrCreate(name);
    }

    @Override
    public FsVolumeGroup fetch(final Integer id) {
        return volumeGroupServiceProvider.get().get(id);
    }

    @Override
    public FsVolumeGroup fetchByName(final String name) {
        return volumeGroupServiceProvider.get().get(name);
    }

    @Override
    public FsVolumeGroup update(final Integer id, final FsVolumeGroup indexVolumeGroup) {
        return volumeGroupServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        volumeGroupServiceProvider.get().delete(id);
        return true;
    }
}
