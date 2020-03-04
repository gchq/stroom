package stroom.data.store.impl.fs.db;

import org.jooq.Condition;
import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FindDataVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.db.util.JooqUtil;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;

public class DataVolumeDaoImpl implements DataVolumeDao {
    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    DataVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    @Override
    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getSetCondition(FS_META_VOLUME.FS_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(FS_META_VOLUME.META_ID, criteria.getMetaIdSet()));

        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> {
            final List<DataVolume> list = context.select(FS_META_VOLUME.META_ID, FS_VOLUME.PATH)
                    .from(FS_META_VOLUME)
                    .join(FS_VOLUME).on(FS_VOLUME.ID.eq(FS_META_VOLUME.FS_VOLUME_ID))
                    .where(conditions)
                    .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                    .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                    .fetch()
                    .map(r -> new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)));
            return ResultPage.createCriterialBasedList(list, criteria);
        });
    }

    /**
     * Return the meta data volumes for a meta id.
     */
    @Override
    public DataVolume findDataVolume(final long metaId) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_META_VOLUME.META_ID, FS_VOLUME.PATH)
                .from(FS_META_VOLUME)
                .join(FS_VOLUME).on(FS_VOLUME.ID.eq(FS_META_VOLUME.FS_VOLUME_ID))
                .where(FS_META_VOLUME.META_ID.eq(metaId))
                .fetchOptional()
                .map(r -> new DataVolumeImpl(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)))
                .orElse(null));
    }

    @Override
    public DataVolume createDataVolume(final long dataId, final FsVolume volume) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> {
            context.insertInto(FS_META_VOLUME, FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                        .values(dataId, volume.getId())
                        .execute();
                return new DataVolumeImpl(dataId, volume.getPath());
        });
    }

    @Override
    public int delete(final List<Long> metaIdList) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .deleteFrom(FS_META_VOLUME)
                .where(FS_META_VOLUME.META_ID.in(metaIdList))
                .execute());
    }

    class DataVolumeImpl implements DataVolume {
        private final long streamId;
        private final String volumePath;

        DataVolumeImpl(final long streamId,
                       final String volumePath) {
            this.streamId = streamId;
            this.volumePath = volumePath;
        }

        @Override
        public long getStreamId() {
            return streamId;
        }

        @Override
        public String getVolumePath() {
            return volumePath;
        }
    }
}
