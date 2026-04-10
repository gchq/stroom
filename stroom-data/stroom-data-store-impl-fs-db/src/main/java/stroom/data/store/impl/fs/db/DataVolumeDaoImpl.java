/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Record2;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;

public class DataVolumeDaoImpl implements DataVolumeDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataVolumeDaoImpl.class);

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
        final List<DataVolume> list = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                        context.select(FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                                .from(FS_META_VOLUME)
                                .where(conditions)
                                .limit(offset, limit)
                                .fetch())
                .map(this::mapRecordToDataVolume);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    /**
     * Return the meta data volumes for a meta id.
     */
    @Override
    public DataVolume findDataVolume(final long metaId) {
        final List<DataVolume> dataVolumes = findDataVolumes(List.of(metaId));
        final DataVolume dataVolume = NullSafe.first(dataVolumes);
        LOGGER.debug("findDataVolume() - metaId: {}, dataVolume: {}", metaId, dataVolume);
        return dataVolume;
    }

    @Override
    public List<DataVolume> findDataVolumes(final Collection<Long> metaIds) {
        if (NullSafe.hasItems(metaIds)) {
            final List<DataVolume> dataVolumes = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                            .select(FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                            .from(FS_META_VOLUME)
                            .where(FS_META_VOLUME.META_ID.in(metaIds))
                            .fetch())
                    .map(this::mapRecordToDataVolume);
            LOGGER.debug("findDataVolumes - metaIds: {}, dataVolumes: {}", metaIds, dataVolumes);
            return dataVolumes;
        } else {
            return Collections.emptyList();
        }
    }

    private DataVolume mapRecordToDataVolume(final Record2<Long, Integer> rec) {
        final long metaId = rec.get(FS_META_VOLUME.META_ID); // NOT_NULL
        final int volumeId = rec.get(FS_META_VOLUME.FS_VOLUME_ID); // NOT_NULL
        final FsVolume volume = fsVolumeCache.get(volumeId);
        Objects.requireNonNull(volume, () -> LogUtil.message(
                "Volume not found for volumeId: {}, metaId: {}", volumeId, metaId));
        return new DataVolumeImpl(metaId, volume);
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
            final Integer count = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                    .deleteFrom(FS_META_VOLUME)
                    .where(FS_META_VOLUME.META_ID.in(metaIdList))
                    .execute());
            LOGGER.debug("delete - metaIdList: {}, count: {}", metaIdList, count);
            return count;
        } else {
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    private record DataVolumeImpl(long metaId, FsVolume volume) implements DataVolume {

        private DataVolumeImpl {
            Objects.requireNonNull(volume);
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DataVolumeImpl that = (DataVolumeImpl) o;
            return metaId == that.metaId
                   && Objects.equals(volume.getId(), that.volume.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(metaId, volume.getId());
        }
    }
}
