/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeGroupRecord;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;

import jakarta.inject.Inject;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
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
    public FsVolumeGroup getOrCreate(final FsVolumeGroup fsVolumeGroup) {
        final Optional<Integer> optional = JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
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
                        .fetchOptional(FS_VOLUME_GROUP.ID)));

        return optional.map(id -> {
            fsVolumeGroup.setId(id);
            fsVolumeGroup.setVersion(1);
            return fsVolumeGroup;
        }).orElse(get(fsVolumeGroup.getName()));
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
        final FsVolumeGroup fsVolumeGroupToDelete = get(name);
        genericDao.delete(fsVolumeGroupToDelete.getId());
    }

    @Override
    public void delete(final int id) {
        genericDao.delete(id);
    }
}
