package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeGroupRecord;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.docref.DocRef;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeGroup.FS_VOLUME_GROUP;

class FsVolumeGroupDaoImpl implements FsVolumeGroupDao {

    static final Function<Record, FsVolumeGroup> RECORD_TO_FS_VOLUME_GROUP_MAPPER = record -> {
        final FsVolumeGroup fsVolumeGroup = new FsVolumeGroup();
        fsVolumeGroup.setId(record.get(FS_VOLUME_GROUP.ID));
        fsVolumeGroup.setVersion(record.get(FS_VOLUME_GROUP.VERSION));
        fsVolumeGroup.setCreateTimeMs(record.get(FS_VOLUME_GROUP.CREATE_TIME_MS));
        fsVolumeGroup.setCreateUser(record.get(FS_VOLUME_GROUP.CREATE_USER));
        fsVolumeGroup.setUpdateTimeMs(record.get(FS_VOLUME_GROUP.UPDATE_TIME_MS));
        fsVolumeGroup.setUpdateUser(record.get(FS_VOLUME_GROUP.UPDATE_USER));
        fsVolumeGroup.setName(record.get(FS_VOLUME_GROUP.NAME));
        fsVolumeGroup.setUuid(record.get(FS_VOLUME_GROUP.UUID));
        fsVolumeGroup.setDefaultVolume(fromDbIsDefaultValue(record.get(FS_VOLUME_GROUP.IS_DEFAULT)));
        return fsVolumeGroup;
    };

    @SuppressWarnings("checkstyle:LineLength")
    private static final BiFunction<FsVolumeGroup, FsVolumeGroupRecord, FsVolumeGroupRecord> FS_VOLUME_GROUP_TO_RECORD_MAPPER =
            (fsVolumeGroup, record) -> {
                record.from(fsVolumeGroup);
                record.set(FS_VOLUME_GROUP.ID, fsVolumeGroup.getId());
                record.set(FS_VOLUME_GROUP.VERSION, fsVolumeGroup.getVersion());
                record.set(FS_VOLUME_GROUP.CREATE_TIME_MS, fsVolumeGroup.getCreateTimeMs());
                record.set(FS_VOLUME_GROUP.CREATE_USER, fsVolumeGroup.getCreateUser());
                record.set(FS_VOLUME_GROUP.UPDATE_TIME_MS, fsVolumeGroup.getUpdateTimeMs());
                record.set(FS_VOLUME_GROUP.UPDATE_USER, fsVolumeGroup.getUpdateUser());
                record.set(FS_VOLUME_GROUP.NAME, fsVolumeGroup.getName());
                record.set(FS_VOLUME_GROUP.UUID, fsVolumeGroup.getUuid());
                record.set(FS_VOLUME_GROUP.IS_DEFAULT, toDbIsDefaultValue(fsVolumeGroup));
                return record;
            };

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final GenericDao<FsVolumeGroupRecord, FsVolumeGroup, Integer> genericDao;

    @Inject
    FsVolumeGroupDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        genericDao = new GenericDao<>(
                fsDataStoreDbConnProvider,
                FS_VOLUME_GROUP,
                FS_VOLUME_GROUP.ID,
                FS_VOLUME_GROUP_TO_RECORD_MAPPER,
                RECORD_TO_FS_VOLUME_GROUP_MAPPER);
    }

    @Override
    public FsVolumeGroup create(final FsVolumeGroup fsVolumeGroup) {
        Objects.requireNonNull(fsVolumeGroup);
        return JooqUtil.transactionResultWithOptimisticLocking(fsDataStoreDbConnProvider, context -> {
            if (fsVolumeGroup.isDefaultVolume()) {
                // Can only have one that is default
                removeCurrentDefault(context);
            }
            return genericDao.create(context, fsVolumeGroup);
        });
    }

    @Override
    public List<FsVolumeGroup> find(final List<String> nameFilters, final boolean allowWildCards) {
        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                FS_VOLUME_GROUP.NAME, nameFilters, true, BooleanOperator.OR);

        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                        context.select()
                                .from(FS_VOLUME_GROUP)
                                .where(condition)
                                .fetch())
                .stream()
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                .toList();
    }

    @Override
    public FsVolumeGroup getOrCreate(final FsVolumeGroup fsVolumeGroup) {
        Objects.requireNonNull(fsVolumeGroup);
        return JooqUtil.transactionResultWithOptimisticLocking(fsDataStoreDbConnProvider, context -> {
            if (fsVolumeGroup.isDefaultVolume()) {
                // Can only have one that is default so ensure all others are not
                context.update(FS_VOLUME_GROUP)
                        .set(FS_VOLUME_GROUP.IS_DEFAULT, (Boolean) null)
                        .where(FS_VOLUME_GROUP.IS_DEFAULT.eq(true))
                        .and(FS_VOLUME_GROUP.UUID.eq(fsVolumeGroup.getUuid()))
                        .execute();
            }
            return genericDao.tryCreate(
                    context,
                    fsVolumeGroup,
                    FS_VOLUME_GROUP.UUID,
                    null,
                    null);
        });
    }

    @Override
    public FsVolumeGroup update(FsVolumeGroup fsVolumeGroup) {
        return JooqUtil.transactionResult(fsDataStoreDbConnProvider, context -> {
            final FsVolumeGroup saved;
            try {
                if (fsVolumeGroup.isDefaultVolume()) {
                    // Can only have one that is default
                    removeCurrentDefault(context);
                }
                saved = genericDao.update(context, fsVolumeGroup);
            } catch (DataAccessException e) {
                if (e.getCause() != null
                        && e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                    final var sqlEx = (SQLIntegrityConstraintViolationException) e.getCause();
                    if (sqlEx.getErrorCode() == 1062
                            && sqlEx.getMessage().contains("Duplicate entry")
                            && sqlEx.getMessage().contains("key")
                            && sqlEx.getMessage().contains(FS_VOLUME_GROUP.NAME.getName())) {
                        throw new RuntimeException("A data volume group already exists with name '"
                                + fsVolumeGroup.getName() + "'");
                    }
                }
                throw e;
            }
            return saved;
        });
    }

    private static Boolean toDbIsDefaultValue(final FsVolumeGroup fsVolumeGroup) {
        return fsVolumeGroup.isDefaultVolume()
                ? Boolean.TRUE
                : null;
    }

    private static boolean fromDbIsDefaultValue(final Boolean isDefault) {
        return NullSafe.isTrue(isDefault);
    }

    private void removeCurrentDefault(final DSLContext context) {
        context.update(FS_VOLUME_GROUP)
                .set(FS_VOLUME_GROUP.IS_DEFAULT, (Boolean) null)
                .where(FS_VOLUME_GROUP.IS_DEFAULT.eq(true))
                .execute();
    }

    @Override
    public FsVolumeGroup get(final int id) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .where(FS_VOLUME_GROUP.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public FsVolumeGroup get(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .where(FS_VOLUME_GROUP.NAME.eq(name))
                        .fetchOptional())
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public FsVolumeGroup get(final DocRef docRef) {
        if (docRef != null) {
            return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                            .select()
                            .from(FS_VOLUME_GROUP)
                            .where(FS_VOLUME_GROUP.UUID.eq(docRef.getUuid()))
                            .fetchOptional())
                    .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                    .orElse(null);
        } else {
            return null;
        }
    }

    @Override
    public FsVolumeGroup getDefaultVolumeGroup() {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .where(FS_VOLUME_GROUP.IS_DEFAULT.eq(true))
                        .fetchOptional())
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public List<String> getNames() {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_VOLUME_GROUP.NAME)
                .from(FS_VOLUME_GROUP)
                .orderBy(FS_VOLUME_GROUP.NAME)
                .fetch(FS_VOLUME_GROUP.NAME));
    }

    @Override
    public List<FsVolumeGroup> getAll() {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .orderBy(FS_VOLUME_GROUP.NAME)
                        .fetch())
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER::apply);
    }

    @Override
    public void delete(final String name) {
        final var fsVolumeGroupToDelete = get(name);
        genericDao.delete(fsVolumeGroupToDelete.getId());
    }

    @Override
    public void delete(int id) {
        genericDao.delete(id);
    }

}
