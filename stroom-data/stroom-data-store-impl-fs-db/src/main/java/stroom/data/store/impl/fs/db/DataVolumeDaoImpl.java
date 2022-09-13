package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FindDataVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.db.util.JooqUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.ResultPage;

import org.jooq.Condition;

import java.util.Collection;
import java.util.List;
import javax.inject.Inject;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;

public class DataVolumeDaoImpl implements DataVolumeDao {

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final PathCreator pathCreator;

    @Inject
    DataVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final PathCreator pathCreator) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        this.pathCreator = pathCreator;
    }

    @Override
    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getSetCondition(FS_META_VOLUME.FS_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(FS_META_VOLUME.META_ID, criteria.getMetaIdSet()));
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<DataVolume> list = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                        context.select(FS_META_VOLUME.META_ID, FS_VOLUME.PATH)
                                .from(FS_META_VOLUME)
                                .join(FS_VOLUME).on(FS_VOLUME.ID.eq(FS_META_VOLUME.FS_VOLUME_ID))
                                .where(conditions)
                                .limit(offset, limit)
                                .fetch())
                .map(r -> createDataVolume(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)));
        return ResultPage.createCriterialBasedList(list, criteria);
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
                        .fetchOptional())
                .map(r -> createDataVolume(r.get(FS_META_VOLUME.META_ID), r.get(FS_VOLUME.PATH)))
                .orElse(null);
    }

    @Override
    public DataVolume createDataVolume(final long metaId, final FsVolume volume) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> {
            context.insertInto(FS_META_VOLUME, FS_META_VOLUME.META_ID, FS_META_VOLUME.FS_VOLUME_ID)
                    .values(metaId, volume.getId())
                    .execute();
            return createDataVolume(metaId, volume.getPath());
        });
    }

    @Override
    public int delete(final List<Long> metaIdList) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .deleteFrom(FS_META_VOLUME)
                .where(FS_META_VOLUME.META_ID.in(metaIdList))
                .execute());
    }

    private DataVolume createDataVolume(final long metaId,
                                        final String volumePath) {
        final String absoluteVolPath = pathCreator.makeAbsolute(
                pathCreator.replaceSystemProperties(volumePath));
        return new DataVolumeImpl(metaId, absoluteVolPath);
    }

    private static class DataVolumeImpl implements DataVolume {

        private final long metaId;
        private final String volumePath;

        DataVolumeImpl(final long metaId,
                       final String volumePath) {
            this.metaId = metaId;
            this.volumePath = volumePath;
        }

        @Override
        public long getMetaId() {
            return metaId;
        }

        @Override
        public String getVolumePath() {
            return volumePath;
        }
    }
}
