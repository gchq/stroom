package stroom.index.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeGroupRecord;
import stroom.index.shared.IndexVolumeGroup;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;

class IndexVolumeGroupDaoImpl implements IndexVolumeGroupDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexVolumeGroupDaoImpl.class);

    static final Function<Record, IndexVolumeGroup> RECORD_TO_INDEX_VOLUME_GROUP_MAPPER = record -> {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setId(record.get(INDEX_VOLUME_GROUP.ID));
        indexVolumeGroup.setVersion(record.get(INDEX_VOLUME_GROUP.VERSION));
        indexVolumeGroup.setCreateTimeMs(record.get(INDEX_VOLUME_GROUP.CREATE_TIME_MS));
        indexVolumeGroup.setCreateUser(record.get(INDEX_VOLUME_GROUP.CREATE_USER));
        indexVolumeGroup.setUpdateTimeMs(record.get(INDEX_VOLUME_GROUP.UPDATE_TIME_MS));
        indexVolumeGroup.setUpdateUser(record.get(INDEX_VOLUME_GROUP.UPDATE_USER));
        indexVolumeGroup.setName(record.get(INDEX_VOLUME_GROUP.NAME));
        indexVolumeGroup.setUuid(record.get(INDEX_VOLUME_GROUP.UUID));
        indexVolumeGroup.setDefaultVolume(record.get(INDEX_VOLUME_GROUP.IS_DEFAULT));
        return indexVolumeGroup;
    };

    @SuppressWarnings("checkstyle:LineLength")
    private static final BiFunction<IndexVolumeGroup, IndexVolumeGroupRecord, IndexVolumeGroupRecord> INDEX_VOLUME_GROUP_TO_RECORD_MAPPER =
            (indexVolumeGroup, record) -> {
                record.from(indexVolumeGroup);
                record.set(INDEX_VOLUME_GROUP.ID, indexVolumeGroup.getId());
                record.set(INDEX_VOLUME_GROUP.VERSION, indexVolumeGroup.getVersion());
                record.set(INDEX_VOLUME_GROUP.CREATE_TIME_MS, indexVolumeGroup.getCreateTimeMs());
                record.set(INDEX_VOLUME_GROUP.CREATE_USER, indexVolumeGroup.getCreateUser());
                record.set(INDEX_VOLUME_GROUP.UPDATE_TIME_MS, indexVolumeGroup.getUpdateTimeMs());
                record.set(INDEX_VOLUME_GROUP.UPDATE_USER, indexVolumeGroup.getUpdateUser());
                record.set(INDEX_VOLUME_GROUP.NAME, indexVolumeGroup.getName());
                record.set(INDEX_VOLUME_GROUP.UUID, indexVolumeGroup.getUuid());
                record.set(INDEX_VOLUME_GROUP.IS_DEFAULT, getDbIsDefaultValue(indexVolumeGroup));
                return record;
            };

    private final IndexDbConnProvider indexDbConnProvider;
    private final Provider<IndexStore> indexStoreProvider;
    private final GenericDao<IndexVolumeGroupRecord, IndexVolumeGroup, Integer> genericDao;

    @Inject
    IndexVolumeGroupDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                            final Provider<IndexStore> indexStoreProvider) {
        this.indexDbConnProvider = indexDbConnProvider;
        this.indexStoreProvider = indexStoreProvider;
        genericDao = new GenericDao<>(
                indexDbConnProvider,
                INDEX_VOLUME_GROUP,
                INDEX_VOLUME_GROUP.ID,
                INDEX_VOLUME_GROUP_TO_RECORD_MAPPER,
                RECORD_TO_INDEX_VOLUME_GROUP_MAPPER);
    }

    @Override
    public IndexVolumeGroup getOrCreate(final IndexVolumeGroup indexVolumeGroup) {
        Optional<Integer> optional = JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .insertInto(INDEX_VOLUME_GROUP,
                                INDEX_VOLUME_GROUP.VERSION,
                                INDEX_VOLUME_GROUP.CREATE_USER,
                                INDEX_VOLUME_GROUP.CREATE_TIME_MS,
                                INDEX_VOLUME_GROUP.UPDATE_USER,
                                INDEX_VOLUME_GROUP.UPDATE_TIME_MS,
                                INDEX_VOLUME_GROUP.NAME,
                                INDEX_VOLUME_GROUP.UUID,
                                INDEX_VOLUME_GROUP.IS_DEFAULT)
                        .values(1,
                                indexVolumeGroup.getCreateUser(),
                                indexVolumeGroup.getCreateTimeMs(),
                                indexVolumeGroup.getUpdateUser(),
                                indexVolumeGroup.getUpdateTimeMs(),
                                indexVolumeGroup.getName(),
                                indexVolumeGroup.getUuid(),
                                indexVolumeGroup.isDefaultVolume())
                        .onDuplicateKeyIgnore()
                        .returning(INDEX_VOLUME_GROUP.ID)
                        .fetchOptional())
                .map(IndexVolumeGroupRecord::getId);

        return optional.map(id -> {
            indexVolumeGroup.setId(id);
            indexVolumeGroup.setVersion(1);
            return indexVolumeGroup;
        }).orElse(get(indexVolumeGroup.getName()));
    }

    @Override
    public IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup) {
        return JooqUtil.transactionResultWithOptimisticLocking(indexDbConnProvider, context -> {
            final IndexVolumeGroup saved;
            try {
                if (indexVolumeGroup.isDefaultVolume()) {
                    // Can only have one that is default
                    setAllOthersNonDefault(indexVolumeGroup, context);
                }
                saved = genericDao.update(indexVolumeGroup);
            } catch (DataAccessException e) {
                if (e.getCause() != null
                        && e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                    final var sqlEx = (SQLIntegrityConstraintViolationException) e.getCause();
                    if (sqlEx.getErrorCode() == 1062
                            && sqlEx.getMessage().contains("Duplicate entry")
                            && sqlEx.getMessage().contains("key")
                            && sqlEx.getMessage().contains(INDEX_VOLUME_GROUP.NAME.getName())) {
                        throw new RuntimeException("An index volume group already exists with name '"
                                + indexVolumeGroup.getName() + "'");
                    }
                }
                throw e;
            }
            return saved;
        });
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME_GROUP)
                        .where(INDEX_VOLUME_GROUP.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME_GROUP)
                        .where(INDEX_VOLUME_GROUP.NAME.eq(name))
                        .fetchOptional())
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public IndexVolumeGroup getDefaultVolumeGroup() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME_GROUP)
                        .where(INDEX_VOLUME_GROUP.IS_DEFAULT.eq(true))
                        .fetchOptional())
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public List<String> getNames() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select(INDEX_VOLUME_GROUP.NAME)
                .from(INDEX_VOLUME_GROUP)
                .orderBy(INDEX_VOLUME_GROUP.NAME)
                .fetch(INDEX_VOLUME_GROUP.NAME));
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME_GROUP)
                        .orderBy(INDEX_VOLUME_GROUP.NAME)
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_GROUP_MAPPER::apply);
    }

    @Override
    public void delete(final String name) {
        final var indexVolumeGroupToDelete = get(name);
        genericDao.delete(indexVolumeGroupToDelete.getId());
    }

    @Override
    public void delete(int id) {
        genericDao.delete(id);
    }

    private static Boolean getDbIsDefaultValue(final IndexVolumeGroup indexVolumeGroup) {
        return indexVolumeGroup.isDefaultVolume()
                ? Boolean.TRUE
                : null;
    }

    private void setAllOthersNonDefault(final IndexVolumeGroup indexVolumeGroup, final DSLContext context) {
        context.update(INDEX_VOLUME_GROUP)
                .set(INDEX_VOLUME_GROUP.IS_DEFAULT, getDbIsDefaultValue(indexVolumeGroup))
                .where(INDEX_VOLUME_GROUP.ID.notEqual(indexVolumeGroup.getId()))
                .execute();
    }
}
