/*
* Copyright 2023 Crown Copyright
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

package stroom.data.store.impl.fs.dao;

import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.db.FsDataStoreDbConnProvider;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeGroupRecord;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupRow;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NonNull;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeGroup.FS_VOLUME_GROUP;

public class FsVolumeGroupDaoImpl implements FsVolumeGroupDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeGroupDaoImpl.class);

    static final Function<Record, FsVolumeGroup> RECORD_TO_FS_VOLUME_GROUP_MAPPER = record -> FsVolumeGroup
            .builder()
            .id(record.get(FS_VOLUME_GROUP.ID))
            .version(record.get(FS_VOLUME_GROUP.VERSION))
            .createTimeMs(record.get(FS_VOLUME_GROUP.CREATE_TIME_MS))
            .createUser(record.get(FS_VOLUME_GROUP.CREATE_USER))
            .updateTimeMs(record.get(FS_VOLUME_GROUP.UPDATE_TIME_MS))
            .updateUser(record.get(FS_VOLUME_GROUP.UPDATE_USER))
            .name(record.get(FS_VOLUME_GROUP.NAME))
            .build();

    @SuppressWarnings("checkstyle:LineLength")
    private static final BiFunction<FsVolumeGroup, FsVolumeGroupRecord, FsVolumeGroupRecord> FS_VOLUME_GROUP_TO_RECORD_MAPPER =
            (fsVolumeGroup, record) -> {
                record.set(FS_VOLUME_GROUP.ID, fsVolumeGroup.getId());
                record.set(FS_VOLUME_GROUP.VERSION, fsVolumeGroup.getVersion());
                record.set(FS_VOLUME_GROUP.CREATE_TIME_MS, fsVolumeGroup.getCreateTimeMs());
                record.set(FS_VOLUME_GROUP.CREATE_USER, fsVolumeGroup.getCreateUser());
                record.set(FS_VOLUME_GROUP.UPDATE_TIME_MS, fsVolumeGroup.getUpdateTimeMs());
                record.set(FS_VOLUME_GROUP.UPDATE_USER, fsVolumeGroup.getUpdateUser());
                record.set(FS_VOLUME_GROUP.NAME, fsVolumeGroup.getName());
                return record;
            };

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final GenericDao<FsVolumeGroupRecord, FsVolumeGroup, Integer> genericDao;

    @Inject
    public FsVolumeGroupDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        genericDao = new GenericDao<>(
                fsDataStoreDbConnProvider,
                FS_VOLUME_GROUP,
                FS_VOLUME_GROUP.ID,
                FS_VOLUME_GROUP_TO_RECORD_MAPPER,
                RECORD_TO_FS_VOLUME_GROUP_MAPPER);
    }

    public FsVolumeGroup create(final FsVolumeGroup fsVolumeGroup) {
        final Integer id = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .insertInto(FS_VOLUME_GROUP,
                        FS_VOLUME_GROUP.VERSION,
                        FS_VOLUME_GROUP.CREATE_USER,
                        FS_VOLUME_GROUP.CREATE_TIME_MS,
                        FS_VOLUME_GROUP.UPDATE_USER,
                        FS_VOLUME_GROUP.UPDATE_TIME_MS,
                        FS_VOLUME_GROUP.NAME)
                .values(1,
                        fsVolumeGroup.getCreateUser(),
                        fsVolumeGroup.getCreateTimeMs(),
                        fsVolumeGroup.getUpdateUser(),
                        fsVolumeGroup.getUpdateTimeMs(),
                        fsVolumeGroup.getName())
                .returning(FS_VOLUME_GROUP.ID)
                .fetchOne(FS_VOLUME_GROUP.ID));
        Objects.requireNonNull(id, "Id is null");
        return fsVolumeGroup
                .copy()
                .id(id)
                .version(1)
                .build();
    }

    @Override
    public FsVolumeGroup getOrCreate(final FsVolumeGroup fsVolumeGroup) {
        // Try fetch first.
        final FsVolumeGroup fetched = fetchByName(fsVolumeGroup.getName());
        if (fetched != null) {
            return fetched;
        }

        try {
            // Try create.
            create(fsVolumeGroup);
        } catch (final RuntimeException e) {
            if (!JooqUtil.isDuplicateKeyException(e)) {
                throw e;
            }
        }

        // Try fetch again.
        return fetchByName(fsVolumeGroup.getName());
    }

    @Override
    public FsVolumeGroup update(final FsVolumeGroup fsVolumeGroup) {
        final FsVolumeGroup saved;
        try {
            saved = genericDao.update(fsVolumeGroup);
        } catch (final DataAccessException e) {
            if (e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
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

//        // If the group name has changed then update indexes to point to the new group name.
//        if (currentGroupName != null && !currentGroupName.equals(saved.getName())) {
//            final IndexStore indexStore = indexStoreProvider.get();
//            if (indexStore != null) {
//                final List<DocRef> indexes = indexStore.list();
//                for (final DocRef docRef : indexes) {
//                    final IndexDoc indexDoc = indexStore.readDocument(docRef);
//                    if (indexDoc.getVolumeGroupName() != null &&
//                            indexDoc.getVolumeGroupName().equals(currentGroupName)) {
//                        indexDoc.setVolumeGroupName(saved.getName());
//                        LOGGER.info("Updating index {} ({}) to change volume group name from {} to {}",
//                                indexDoc.getName(),
//                                indexDoc.getUuid(),
//                                currentGroupName,
//                                saved.getName());
//                        indexStore.writeDocument(indexDoc);
//                    }
//                }
//            }
//        }

        return saved;
    }

    @Override
    public FsVolumeGroup fetchById(final int id) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .where(FS_VOLUME_GROUP.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_FS_VOLUME_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public FsVolumeGroup fetchByName(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME_GROUP)
                        .where(FS_VOLUME_GROUP.NAME.eq(name))
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
    public ResultPage<FsVolumeGroupRow> findExtended(final ExpressionCriteria criteria) {
        final Field<Integer> volumeCountField = DSL.count(FS_VOLUME.ID)
                .as("volume_count");
        final Field<String> volumeTypesField = DSL.groupConcatDistinct(FS_VOLUME.VOLUME_TYPE)
                .as("volume_types");

        final List<FsVolumeGroupRow> list = JooqUtil.contextResult(
                        fsDataStoreDbConnProvider, context -> context
                                .select(
                                        FS_VOLUME_GROUP.ID,
                                        FS_VOLUME_GROUP.VERSION,
                                        FS_VOLUME_GROUP.CREATE_TIME_MS,
                                        FS_VOLUME_GROUP.CREATE_USER,
                                        FS_VOLUME_GROUP.UPDATE_TIME_MS,
                                        FS_VOLUME_GROUP.UPDATE_USER,
                                        FS_VOLUME_GROUP.NAME,
                                        volumeCountField,
                                        volumeTypesField)
                                .from(FS_VOLUME_GROUP)
                                .leftOuterJoin(FS_VOLUME)
                                .on(FS_VOLUME.FK_FS_VOLUME_GROUP_ID.eq(FS_VOLUME_GROUP.ID))
                                .groupBy(FS_VOLUME_GROUP.ID)
                                .orderBy(FS_VOLUME_GROUP.NAME)
                                .fetch())
                .map(record -> {
                    final FsVolumeGroup group = RECORD_TO_FS_VOLUME_GROUP_MAPPER.apply(record);
                    final Integer volumeCount = record.get(volumeCountField);
                    final String volumeTypesStr = record.get(volumeTypesField);
                    final List<FsVolumeType> volumeTypes = parseVolumeTypesList(volumeTypesStr);
                    return new FsVolumeGroupRow(group, NullSafe.getInt(volumeCount), volumeTypes);
                });

        return ResultPage.createUnboundedList(list);
    }

    private static @NonNull List<FsVolumeType> parseVolumeTypesList(final String volumeTypesStr) {
        final List<FsVolumeType> volumeTypes = new ArrayList<>();
        if (NullSafe.isNonBlankString(volumeTypesStr)) {
            for (final String part : volumeTypesStr.split(",")) {
                try {
                    final FsVolumeType fsVolumeType = FsVolumeType.fromId(Integer.parseInt(part.trim()));
                    if (fsVolumeType != null) {
                        volumeTypes.add(fsVolumeType);
                    } else {
                        LOGGER.error("Cannot convert '{}' to a FsVolumeType", part);
                    }
                } catch (final NumberFormatException e) {
                    LOGGER.error("Unknown FsVolumeType for id '{}'", part);
                }
            }
        }
        return volumeTypes;
    }

    @Override
    public void delete(final String name) {
        final FsVolumeGroup fsVolumeGroupToDelete = fetchByName(name);
        genericDao.delete(fsVolumeGroupToDelete.getId());
    }

    @Override
    public void delete(final int id) {
        genericDao.delete(id);
    }
}
