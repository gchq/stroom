package stroom.index.impl.db;

import org.jooq.Record;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeRecord;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP_LINK;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;

class IndexVolumeDaoImpl implements IndexVolumeDao {
    static final Function<Record, IndexVolume> RECORD_TO_INDEX_VOLUME_MAPPER = record -> {
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setId(record.get(INDEX_VOLUME.ID));
        indexVolume.setVersion(record.get(INDEX_VOLUME.VERSION));
        indexVolume.setCreateTimeMs(record.get(INDEX_VOLUME.CREATE_TIME_MS));
        indexVolume.setCreateUser(record.get(INDEX_VOLUME.CREATE_USER));
        indexVolume.setUpdateTimeMs(record.get(INDEX_VOLUME.UPDATE_TIME_MS));
        indexVolume.setUpdateUser(record.get(INDEX_VOLUME.UPDATE_USER));
        indexVolume.setPath(record.get(INDEX_VOLUME.PATH));
        indexVolume.setNodeName(record.get(INDEX_VOLUME.NODE_NAME));
//        indexVolume.setStatus(record.get);
        indexVolume.setBytesLimit(record.get(INDEX_VOLUME.BYTES_LIMIT));
        indexVolume.setBytesUsed(record.get(INDEX_VOLUME.BYTES_USED));
        indexVolume.setBytesFree(record.get(INDEX_VOLUME.BYTES_FREE));
        indexVolume.setBytesTotal(record.get(INDEX_VOLUME.BYTES_TOTAL));
        indexVolume.setStatusMs(record.get(INDEX_VOLUME.STATUS_MS));
        return indexVolume;
    };

    private static final BiFunction<IndexVolume, IndexVolumeRecord, IndexVolumeRecord> INDEX_VOLUME_TO_RECORD_MAPPER = (indexVolume, record) -> {
        record.from(indexVolume);
        record.set(INDEX_VOLUME.ID, indexVolume.getId());
        record.set(INDEX_VOLUME.VERSION, indexVolume.getVersion());
        record.set(INDEX_VOLUME.CREATE_TIME_MS, indexVolume.getCreateTimeMs());
        record.set(INDEX_VOLUME.CREATE_USER, indexVolume.getCreateUser());
        record.set(INDEX_VOLUME.UPDATE_TIME_MS, indexVolume.getUpdateTimeMs());
        record.set(INDEX_VOLUME.UPDATE_USER, indexVolume.getUpdateUser());
        record.set(INDEX_VOLUME.PATH, indexVolume.getPath());
        record.set(INDEX_VOLUME.NODE_NAME, indexVolume.getNodeName());
        record.set(INDEX_VOLUME.BYTES_LIMIT, indexVolume.getBytesLimit());
        record.set(INDEX_VOLUME.BYTES_USED, indexVolume.getBytesUsed());
        record.set(INDEX_VOLUME.BYTES_FREE, indexVolume.getBytesFree());
        record.set(INDEX_VOLUME.BYTES_TOTAL, indexVolume.getBytesTotal());
        record.set(INDEX_VOLUME.STATUS_MS, indexVolume.getStatusMs());
        return record;
    };

    private final ConnectionProvider connectionProvider;
    private final GenericDao<IndexVolumeRecord, IndexVolume, Integer> genericDao;

    @Inject
    IndexVolumeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        genericDao = new GenericDao<>(INDEX_VOLUME, INDEX_VOLUME.ID, IndexVolume.class, connectionProvider);
        genericDao.setRecordToObjectMapper(RECORD_TO_INDEX_VOLUME_MAPPER);
        genericDao.setObjectToRecordMapper(INDEX_VOLUME_TO_RECORD_MAPPER);
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return genericDao.create(indexVolume);
    }

    @Override
    public Optional<IndexVolume> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public List<IndexVolume> getAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
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
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
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
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
    }

    @Override
    public void addVolumeToGroup(final int volumeId,
                                 final String name) {
        JooqUtil.transaction(connectionProvider, context -> {
            final Optional<Integer> optionalGroupId = context
                    .select(INDEX_VOLUME_GROUP.ID)
                    .from(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                    .fetchOptional(INDEX_VOLUME_GROUP.ID);

            final int groupId = optionalGroupId.orElseThrow(() -> new RuntimeException("No group found with name '" + name + "'"));

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
    public void removeVolumeFromGroup(final int volumeId,
                                      final String name) {
        JooqUtil.transaction(connectionProvider, context -> {
            final Optional<Integer> optionalGroupId = context
                    .select(INDEX_VOLUME_GROUP.ID)
                    .from(INDEX_VOLUME_GROUP)
                    .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                    .fetchOptional(INDEX_VOLUME_GROUP.ID);

            final int groupId = optionalGroupId.orElseThrow(() -> new RuntimeException("No group found with name '" + name + "'"));

            context.deleteFrom(INDEX_VOLUME_GROUP_LINK)
                    .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                    .and(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_GROUP_ID.eq(groupId))
                    .execute();
        });
    }

    @Override
    public void clearVolumeGroupMemberships(final int volumeId) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_VOLUME_GROUP_LINK)
                .where(INDEX_VOLUME_GROUP_LINK.FK_INDEX_VOLUME_ID.eq(volumeId))
                .execute()
        );
    }
}
