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

package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class IndexVolumeGroupResourceImpl implements IndexVolumeGroupResource {

    private final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider;

    @Inject
    IndexVolumeGroupResourceImpl(final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider) {
        this.indexVolumeGroupServiceProvider = indexVolumeGroupServiceProvider;
    }

    @Override
    public ResultPage<IndexVolumeGroup> find(final ExpressionCriteria request) {
        return ResultPage.createUnboundedList(indexVolumeGroupServiceProvider.get().getAll());
    }

    @Override
    public IndexVolumeGroup create(final String name) {
        return indexVolumeGroupServiceProvider.get().getOrCreate(name);
    }

    @Override
    public IndexVolumeGroup fetch(final Integer id) {
        return indexVolumeGroupServiceProvider.get().get(id);
    }

    @Override
    public IndexVolumeGroup fetchByName(final String name) {
        return indexVolumeGroupServiceProvider.get().get(name);
    }

    @Override
    public IndexVolumeGroup update(final Integer id, final IndexVolumeGroup indexVolumeGroup) {
        return indexVolumeGroupServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        indexVolumeGroupServiceProvider.get().delete(id);
        return true;
    }
}
