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

import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FindDataVolumeCriteria;
import stroom.data.store.impl.fs.FsVolumeCache;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.db.util.JooqUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;

public class DataVolumeDaoImpl implements DataVolumeDao {

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final FsVolumeCache fsVolumeCache;

    @Inject
    DataVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final FsVolumeCache fsVolumeCache) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        this.fsVolumeCache = fsVolumeCache;
    }

    @Override
    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getSetCondition(FS_META_VOLUME.FS_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(FS_META_VOLUME.META_ID, criteria.getMetaIdSet()));
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final Map<Integer, FsVolume> volumeCache = new HashMap<>();
        final List<DataVolume> list = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                        context.select(FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                                .from(FS_META_VOLUME)
                                .where(conditions)
                                .limit(offset, limit)
                                .fetch())
                .map(r -> {
                    final Integer volumeId = r.get(FS_META_VOLUME.FS_VOLUME_ID);
                    final FsVolume volume = volumeCache.computeIfAbsent(volumeId, fsVolumeCache::get);
                    return new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), volume);
                });
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    /**
     * Return the meta data volumes for a meta id.
     */
    @Override
    public DataVolume findDataVolume(final long metaId) {
        final Map<Integer, FsVolume> volumeCache = new HashMap<>();
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select(FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                        .from(FS_META_VOLUME)
                        .where(FS_META_VOLUME.META_ID.eq(metaId))
                        .fetchOptional())
                .map(r -> {
                    final Integer volumeId = r.get(FS_META_VOLUME.FS_VOLUME_ID);
                    final FsVolume volume = volumeCache.computeIfAbsent(volumeId, fsVolumeCache::get);
                    return new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), volume);
                })
                .orElse(null);
    }

    @Override
    public DataVolume createDataVolume(final long metaId, final FsVolume volume) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> {
            context.insertInto(FS_META_VOLUME, FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                    .values(metaId, volume.getId())
                    .execute();
            return new DataVolumeImpl(metaId, volume);
        });
    }

    @Override
    public int delete(final Collection<Long> metaIdList) {
        if (NullSafe.hasItems(metaIdList)) {
            return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                    .deleteFrom(FS_META_VOLUME)
                    .where(FS_META_VOLUME.META_ID.in(metaIdList))
                    .execute());
        } else {
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class DataVolumeImpl implements DataVolume {

        private final long metaId;
        private final FsVolume volume;

        DataVolumeImpl(final long metaId,
                       final FsVolume volume) {
            this.metaId = metaId;
            this.volume = volume;
        }

        @Override
        public long getMetaId() {
            return metaId;
        }

        @Override
        public FsVolume getVolume() {
            return volume;
        }
    }
}
