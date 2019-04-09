package stroom.index.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeGroupRecord;
import stroom.index.shared.IndexVolumeGroup;

import javax.inject.Inject;
import java.util.List;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP_LINK;

class IndexVolumeGroupDaoImpl implements IndexVolumeGroupDao {
    private final ConnectionProvider connectionProvider;
    private final GenericDao<IndexVolumeGroupRecord, IndexVolumeGroup, Long> genericDao;

    @Inject
    IndexVolumeGroupDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        genericDao = new GenericDao<>(INDEX_VOLUME_GROUP, INDEX_VOLUME_GROUP.ID, IndexVolumeGroup.class, connectionProvider);
    }

    @Override
    public IndexVolumeGroup create(final IndexVolumeGroup indexVolumeGroup) {
//        return JooqUtil.contextResult(connectionProvider, context -> {
//            context
//                    .insertInto(INDEX_VOLUME_GROUP,
//                            INDEX_VOLUME_GROUP.NAME,
//                            INDEX_VOLUME_GROUP.CREATE_USER,
//                            INDEX_VOLUME_GROUP.CREATE_TIME_MS)
//                    .values(indexVolumeGroup.getName(), indexVolumeGroup.getCreateUser(), indexVolumeGroup.getCreateTimeMs())
//                    .onDuplicateKeyIgnore()
//                    .execute();
//
//            return context
//                    .select()
//                    .from(INDEX_VOLUME_GROUP)
//                    .where(INDEX_VOLUME_GROUP.NAME.eq(indexVolumeGroup.getName()))
//                    .fetchOneInto(IndexVolumeGroup.class);
//        });

        return genericDao.create(indexVolumeGroup);
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME_GROUP)
                .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                .fetchOneInto(IndexVolumeGroup.class)
        );
    }

    @Override
    public List<String> getNames() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(INDEX_VOLUME_GROUP.NAME)
                .from(INDEX_VOLUME_GROUP)
                .fetch(INDEX_VOLUME_GROUP.NAME));
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME_GROUP)
                .fetchInto(IndexVolumeGroup.class)
        );
    }

    @Override
    public void delete(final String name) {
        JooqUtil.transaction(connectionProvider, context -> {
            context
                    .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                    .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID.in(
                            context
                                    .select(INDEX_VOLUME_GROUP.ID)
                                    .from(INDEX_VOLUME_GROUP)
                                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))))
                    .execute();
            context.deleteFrom(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name)).execute();
        });
    }
}
