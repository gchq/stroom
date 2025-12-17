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

package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.db.util.GenericDao;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

@Singleton
public class FsVolumeStateDaoImpl implements FsVolumeStateDao {

    private final GenericDao<FsVolumeStateRecord, FsVolumeState, Integer> genericDao;

    @Inject
    FsVolumeStateDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        genericDao = new GenericDao<>(
                fsDataStoreDbConnProvider,
                FS_VOLUME_STATE,
                FS_VOLUME_STATE.ID,
                FsVolumeState.class);
    }

    @Override
    public FsVolumeState create(final FsVolumeState volumeState) {
        return genericDao.create(volumeState);
    }

    @Override
    public FsVolumeState update(final FsVolumeState volumeState) {
        return genericDao.update(volumeState);
    }

    @Override
    public FsVolumeState updateWithoutOptimisticLocking(final FsVolumeState volumeState) {
        return genericDao.updateWithoutOptimisticLocking(volumeState);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<FsVolumeState> fetch(final int id) {
        return genericDao.fetch(id);
    }
}
