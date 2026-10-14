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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class SqlTemporalStoreResourceImpl implements SqlTemporalStoreResource {

    private final Provider<UpdatableSqlTemporalStore> updatableSqlTemporalStoreProvider;

    @Inject
    SqlTemporalStoreResourceImpl(
            final Provider<UpdatableSqlTemporalStore> updatableSqlTemporalStoreProvider) {
        this.updatableSqlTemporalStoreProvider = updatableSqlTemporalStoreProvider;
    }

    @Override
    public Boolean clear(final DocRef docRef) {
        updatableSqlTemporalStoreProvider.get().clear(docRef);
        return true;
    }

    @Override
    public Long count(final DocRef docRef) {
        return updatableSqlTemporalStoreProvider.get().count(docRef);
    }

    @Override
    public TemporalEntry create(final TemporalEntry entry) {
        return updatableSqlTemporalStoreProvider.get().create(entry);
    }

    @Override
    public TemporalEntry update(final TemporalEntry entry) {
        return updatableSqlTemporalStoreProvider.get().update(entry);
    }


    @Override
    public Boolean delete(final TemporalEntryId id) {
        return updatableSqlTemporalStoreProvider.get().delete(id);
    }

    @Override
    public ResultPage<TemporalEntry> find(final ExpressionCriteria criteria) {
        return updatableSqlTemporalStoreProvider.get().find(criteria);
    }

    @Override
    public List<TemporalEntry> fetchAtTime(final FetchAtTimeRequest request) {
        return updatableSqlTemporalStoreProvider.get().fetchAtTime(request);
    }

    @Override
    public List<TemporalEntry> fetchAll(final String mapName) {
        return updatableSqlTemporalStoreProvider.get().fetchAll(mapName);
    }

    @Override
    public TemporalStoreTimeRange getTimeRange(final String mapName) {
        return updatableSqlTemporalStoreProvider.get().getTimeRange(mapName);
    }

    @Override
    public ApplyChangesResult applyChanges(final ApplyChangesRequest request) {
        return updatableSqlTemporalStoreProvider.get().applyChanges(request);
    }
}
