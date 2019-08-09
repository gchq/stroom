package stroom.index.impl.db;

import org.jooq.Record;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeGroupRecord;
import stroom.index.shared.IndexVolumeGroup;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;

class IndexVolumeGroupDaoImpl implements IndexVolumeGroupDao {
    private static final Function<Record, IndexVolumeGroup> RECORD_TO_INDEX_VOLUME_GROUP_MAPPER = record -> {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setId(record.get(INDEX_VOLUME_GROUP.ID));
        indexVolumeGroup.setVersion(record.get(INDEX_VOLUME_GROUP.VERSION));
        indexVolumeGroup.setCreateTimeMs(record.get(INDEX_VOLUME_GROUP.CREATE_TIME_MS));
        indexVolumeGroup.setCreateUser(record.get(INDEX_VOLUME_GROUP.CREATE_USER));
        indexVolumeGroup.setUpdateTimeMs(record.get(INDEX_VOLUME_GROUP.UPDATE_TIME_MS));
        indexVolumeGroup.setUpdateUser(record.get(INDEX_VOLUME_GROUP.UPDATE_USER));
        indexVolumeGroup.setName(record.get(INDEX_VOLUME_GROUP.NAME));
        return indexVolumeGroup;
    };

    private static final BiFunction<IndexVolumeGroup, IndexVolumeGroupRecord, IndexVolumeGroupRecord> INDEX_VOLUME_GROUP_TO_RECORD_MAPPER = (indexVolumeGroup, record) -> {
        record.from(indexVolumeGroup);
        record.set(INDEX_VOLUME_GROUP.ID, indexVolumeGroup.getId());
        record.set(INDEX_VOLUME_GROUP.VERSION, indexVolumeGroup.getVersion());
        record.set(INDEX_VOLUME_GROUP.CREATE_TIME_MS, indexVolumeGroup.getCreateTimeMs());
        record.set(INDEX_VOLUME_GROUP.CREATE_USER, indexVolumeGroup.getCreateUser());
        record.set(INDEX_VOLUME_GROUP.UPDATE_TIME_MS, indexVolumeGroup.getUpdateTimeMs());
        record.set(INDEX_VOLUME_GROUP.UPDATE_USER, indexVolumeGroup.getUpdateUser());
        record.set(INDEX_VOLUME_GROUP.NAME, indexVolumeGroup.getName());
        return record;
    };

    private final ConnectionProvider connectionProvider;
    private final GenericDao<IndexVolumeGroupRecord, IndexVolumeGroup, Integer> genericDao;

    @Inject
    IndexVolumeGroupDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        genericDao = new GenericDao<>(INDEX_VOLUME_GROUP, INDEX_VOLUME_GROUP.ID, IndexVolumeGroup.class, connectionProvider);
        genericDao.setRecordToObjectMapper(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER);
        genericDao.setObjectToRecordMapper(INDEX_VOLUME_GROUP_TO_RECORD_MAPPER);
    }

    @Override
    public IndexVolumeGroup getOrCreate(final IndexVolumeGroup indexVolumeGroup) {
        Optional<Integer> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(INDEX_VOLUME_GROUP,
                        INDEX_VOLUME_GROUP.VERSION,
                        INDEX_VOLUME_GROUP.CREATE_USER,
                        INDEX_VOLUME_GROUP.CREATE_TIME_MS,
                        INDEX_VOLUME_GROUP.UPDATE_USER,
                        INDEX_VOLUME_GROUP.UPDATE_TIME_MS,
                        INDEX_VOLUME_GROUP.NAME)
                .values(1,
                        indexVolumeGroup.getCreateUser(),
                        indexVolumeGroup.getCreateTimeMs(),
                        indexVolumeGroup.getUpdateUser(),
                        indexVolumeGroup.getUpdateTimeMs(),
                        indexVolumeGroup.getName())
                .onDuplicateKeyIgnore()
                .returning(INDEX_VOLUME_GROUP.ID)
                .fetchOptional()
                .map(IndexVolumeGroupRecord::getId));

        return optional.map(id -> {
            indexVolumeGroup.setId(id);
            indexVolumeGroup.setVersion(1);
            return indexVolumeGroup;
        }).orElse(get(indexVolumeGroup.getName()));
    }

    @Override
    public IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup) {
        return genericDao.update(indexVolumeGroup);
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME_GROUP)
                .where(INDEX_VOLUME_GROUP.ID.eq(id))
                .fetchOptional()
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER)
                .orElse(null));
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(INDEX_VOLUME_GROUP)
                .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                .fetchOptional()
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER)
                .orElse(null));
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
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER::apply));
    }

    @Override
    public void delete(final int id) {
        genericDao.delete(id);
    }

    @Override
    public void delete(final String name) {
        final var indexVolumeGroup = this.get(name);
        genericDao.delete(indexVolumeGroup.getId());
    }
}
