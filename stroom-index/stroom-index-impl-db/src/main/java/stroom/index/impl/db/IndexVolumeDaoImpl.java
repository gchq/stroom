package stroom.index.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeRecord;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP_LINK;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;

class IndexVolumeDaoImpl implements IndexVolumeDao {
    private final ConnectionProvider connectionProvider;
    private final GenericDao<IndexVolumeRecord, IndexVolume, Long> genericDao;

    @Inject
    IndexVolumeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        genericDao = new GenericDao<>(INDEX_VOLUME, INDEX_VOLUME.ID, IndexVolume.class, connectionProvider);
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return genericDao.create(indexVolume);
    }

    @Override
    public Optional<IndexVolume> fetch(final long id) {
        return genericDao.fetch(id);
    }

    @Override
    public boolean delete(final long id) {
        return genericDao.delete(id);
    }

    @Override
    public List<IndexVolume> getAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .fetchInto(IndexVolume.class));
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME)
                .innerJoin(INDEX_VOLUME_GROUP_LINK)
                .on(INDEX_VOLUME.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID))
                .innerJoin(INDEX_VOLUME_GROUP)
                .on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID))
                .where(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                .fetchInto(IndexVolume.class)
        );
    }

    @Override
    public List<IndexVolume> getVolumesInGroupOnNode(final String groupName,
                                                     final String nodeName) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_VOLUME)
                .innerJoin(INDEX_VOLUME_GROUP_LINK)
                .on(INDEX_VOLUME.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID))
                .innerJoin(INDEX_VOLUME_GROUP)
                .on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID))
                .where(INDEX_VOLUME.NODE_NAME.eq(nodeName))
                .and(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                .fetchInto(IndexVolume.class)
        );
    }

    @Override
    public void addVolumeToGroup(final Long volumeId,
                                 final String name) {
        JooqUtil.transaction(connectionProvider, context -> {
            final Optional<Long> optionalGroupId = context
                    .select(INDEX_VOLUME_GROUP.ID)
                    .from(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                    .fetchOptional(INDEX_VOLUME_GROUP.ID);

            final long groupId = optionalGroupId.orElseThrow(() -> new RuntimeException("No group found with name '" + name + "'"));

            context
                    .insertInto(INDEX_VOLUME_GROUP_LINK,
                            INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID,
                            INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID)
                    .values(volumeId, groupId)
                    .onDuplicateKeyIgnore()
                    .execute();
        });
    }

    @Override
    public void removeVolumeFromGroup(final Long volumeId,
                                      final String name) {
        JooqUtil.transaction(connectionProvider, context -> {
            final Optional<Long> optionalGroupId = context
                    .select(INDEX_VOLUME_GROUP.ID)
                    .from(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                    .fetchOptional(INDEX_VOLUME_GROUP.ID);

            final long groupId = optionalGroupId.orElseThrow(() -> new RuntimeException("No group found with name '" + name + "'"));

            context.deleteFrom(INDEX_VOLUME_GROUP_LINK)
                    .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                    .and(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID.eq(groupId))
                    .execute();
        });
    }

    @Override
    public void clearVolumeGroupMemberships(final Long volumeId) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                .execute()
        );
    }
}
