package stroom.index.impl.db;

import org.jooq.Record;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeRecord;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

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
        indexVolume.setIndexVolumeGroupId(record.get(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID));
        final Byte state = record.get(INDEX_VOLUME.STATE);
        if (state != null) {
            indexVolume.setState(VolumeUseState.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(state));
        }
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
        record.set(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID, indexVolume.getIndexVolumeGroupId());
        if ( indexVolume.getState() != null) {
            record.set(INDEX_VOLUME.STATE, indexVolume.getState().getPrimitiveValue());
        }
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
    public IndexVolume update(IndexVolume indexVolume) {
       return genericDao.update(indexVolume);
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

}
