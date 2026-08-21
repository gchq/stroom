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

package stroom.data.store.impl.fs.dao;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.db.FsDataStoreDbConnProvider;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeRecord;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.db.util.JooqUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.TableField;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeGroup.FS_VOLUME_GROUP;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

@Singleton
public class FsVolumeDaoImpl implements FsVolumeDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeDaoImpl.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    public FsVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    @Override
    public FsVolume create(final FsVolume fileVolume) {
        byte[] data = null;
        final String json = fileVolume.getS3ClientConfigData();
        if (json != null && !json.isBlank()) {
            // Check we can deserialise the json string.
            JsonUtil.readValue(json, S3ClientConfig.class);
            data = json.getBytes(StandardCharsets.UTF_8);
        }
        final byte[] finalData = data;
        final FsVolumeType volumeType = Objects.requireNonNullElse(fileVolume.getVolumeType(), FsVolumeType.STANDARD);

        final Integer id = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .insertInto(FS_VOLUME)
                .columns(FS_VOLUME.VERSION,
                        FS_VOLUME.CREATE_TIME_MS,
                        FS_VOLUME.CREATE_USER,
                        FS_VOLUME.UPDATE_TIME_MS,
                        FS_VOLUME.UPDATE_USER,
                        FS_VOLUME.PATH,
                        FS_VOLUME.STATUS,
                        FS_VOLUME.BYTE_LIMIT,
                        FS_VOLUME.FK_FS_VOLUME_STATE_ID,
                        FS_VOLUME.VOLUME_TYPE,
                        FS_VOLUME.FK_FS_VOLUME_GROUP_ID,
                        FS_VOLUME.DATA)
                .values(1,
                        fileVolume.getCreateTimeMs(),
                        fileVolume.getCreateUser(),
                        fileVolume.getUpdateTimeMs(),
                        fileVolume.getUpdateUser(),
                        fileVolume.getPath(),
                        fileVolume.getStatus().getPrimitiveValue(),
                        fileVolume.getByteLimit(),
                        fileVolume.getVolumeState().getId(),
                        volumeType.getId(),
                        fileVolume.getVolumeGroupId(),
                        finalData)
                .returning(FS_VOLUME.ID)
                .fetchOne(FS_VOLUME.ID));
        Objects.requireNonNull(id, "Null DB id");
        // Re-fetch so we return what is actually in the DB.
        return fetch(id);
    }

    @Override
    public FsVolume update(final FsVolume fileVolume) {
        final FsVolumeRecord record = FS_VOLUME.newRecord();
        volumeToRecord(fileVolume, record);
        final FsVolumeRecord persistedRecord = JooqUtil.updateWithOptimisticLocking(fsDataStoreDbConnProvider, record);
        return recordToVolume(
                persistedRecord,
                fileVolume.getVolumeState(),
                fileVolume.getVolumeGroup());
    }

    @Override
    public int delete(final int id) {
        return JooqUtil.transactionResult(fsDataStoreDbConnProvider, context -> {
            final Optional<Integer> stateIdOptional = context
                    .select(FS_VOLUME.FK_FS_VOLUME_STATE_ID)
                    .from(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .fetchOptional(FS_VOLUME.FK_FS_VOLUME_STATE_ID);

            final int result = context
                    .deleteFrom(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .execute();

            stateIdOptional.ifPresent(stateId -> context
                    .deleteFrom(FS_VOLUME_STATE)
                    .where(FS_VOLUME_STATE.ID.eq(stateId))
                    .execute());

            return result;
        });
    }

    @Override
    public FsVolume fetch(final int id) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE).on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .where(FS_VOLUME.ID.eq(id))
                        .fetchOptional())
                .map(this::recordToVolume)
                .orElse(null);
    }

    @Override
    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                volumeStatusCriteriaSetToCondition(FS_VOLUME.STATUS, criteria.getSelection()),
                Optional.ofNullable(criteria.getGroup())
                        .map(group -> FS_VOLUME.FK_FS_VOLUME_GROUP_ID.eq(group.getId())));

        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final Result<Record> result = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select()
                .from(FS_VOLUME)
                .join(FS_VOLUME_STATE).on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                .where(conditions)
                .limit(offset, limit)
                .fetch());

        final Caches caches = new Caches();
        final List<FsVolume> list = result.map(record -> recordToVolume(record, caches));
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public Set<FsVolume> get(final String path) {
        final Caches caches = new Caches();
        return new HashSet<>(JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .where(FS_VOLUME.PATH.eq(path))
                        .fetch())
                .map(record -> recordToVolume(record, caches)));
    }

    @Override
    public List<FsVolume> getAll() {
        final Caches caches = new Caches();
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE).on(FS_VOLUME.FK_FS_VOLUME_STATE_ID.eq(FS_VOLUME_STATE.ID))
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .fetch())
                .map(record -> recordToVolume(record, caches));
    }

    @Override
    public List<FsVolume> getVolumesInGroup(final String groupName) {
        final Caches caches = new Caches();
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE).on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .where(FS_VOLUME_GROUP.NAME.eq(groupName))
                        .fetch())
                .map(record -> recordToVolume(record, caches));
    }

    @Override
    public List<FsVolume> getVolumesInGroup(final int groupId) {
        final Caches caches = new Caches();
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE).on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .where(FS_VOLUME.FK_FS_VOLUME_GROUP_ID.eq(groupId))
                        .fetch())
                .map(record -> recordToVolume(record, caches));
    }

    private void volumeToRecord(final FsVolume fileVolume, final FsVolumeRecord record) {
        byte[] data = null;
        final String json = fileVolume.getS3ClientConfigData();
        if (json != null && !json.isBlank()) {
            // Check we can deserialise the json string.
            JsonUtil.readValue(json, S3ClientConfig.class);
            data = json.getBytes(StandardCharsets.UTF_8);
        }

        final FsVolumeType volumeType = Objects.requireNonNullElse(fileVolume.getVolumeType(), FsVolumeType.STANDARD);
        record.set(FS_VOLUME.ID, fileVolume.getId());
        record.set(FS_VOLUME.VERSION, fileVolume.getVersion());
        record.set(FS_VOLUME.CREATE_TIME_MS, fileVolume.getCreateTimeMs());
        record.set(FS_VOLUME.CREATE_USER, fileVolume.getCreateUser());
        record.set(FS_VOLUME.UPDATE_TIME_MS, fileVolume.getUpdateTimeMs());
        record.set(FS_VOLUME.UPDATE_USER, fileVolume.getUpdateUser());
        record.set(FS_VOLUME.PATH, fileVolume.getPath());
        record.set(FS_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
        record.set(FS_VOLUME.BYTE_LIMIT, fileVolume.getByteLimit());
        record.set(FS_VOLUME.FK_FS_VOLUME_STATE_ID, fileVolume.getVolumeState().getId());
        record.set(FS_VOLUME.VOLUME_TYPE, volumeType.getId());
        record.set(FS_VOLUME.FK_FS_VOLUME_GROUP_ID, fileVolume.getVolumeGroupId());
        record.set(FS_VOLUME.DATA, data);
    }

    private FsVolume recordToVolume(final Record record) {
        final FsVolumeState fileSystemVolumeState = recordToVolumeState(record);
        final FsVolumeGroup fsVolumeGroup = recordToVolumeGroup(record);
        return recordToVolume(record, fileSystemVolumeState, fsVolumeGroup);
    }

    private FsVolume recordToVolume(final Record record,
                                    final Caches caches) {
        // No need to cache vol state as it is 1:1 with volume
        final FsVolumeState fileSystemVolumeState = recordToVolumeState(record);
        final FsVolumeGroup fsVolumeGroup = recordToVolumeGroup(record, caches);
        return recordToVolume(record, fileSystemVolumeState, fsVolumeGroup);
    }

    private static @NonNull FsVolumeState recordToVolumeState(final Record record) {
        return new FsVolumeState(
                record.get(FS_VOLUME_STATE.ID),
                record.get(FS_VOLUME_STATE.VERSION),
                record.get(FS_VOLUME_STATE.BYTES_USED),
                record.get(FS_VOLUME_STATE.BYTES_FREE),
                record.get(FS_VOLUME_STATE.BYTES_TOTAL),
                record.get(FS_VOLUME_STATE.UPDATE_TIME_MS));
    }

    private FsVolumeGroup recordToVolumeGroup(final Record record,
                                              final Caches caches) {
        if (caches.volGrpCache != null) {
            return caches.volGrpCache.computeIfAbsent(
                    record.get(FS_VOLUME_GROUP.ID),
                    ignored ->
                            recordToVolumeGroup(record));
        } else {
            return recordToVolumeGroup(record);
        }
    }

    private FsVolumeGroup recordToVolumeGroup(final Record record) {
        return FsVolumeGroup.builder()
                .id(record.get(FS_VOLUME_GROUP.ID))
                .version(record.get(FS_VOLUME_GROUP.VERSION))
                .name(record.get(FS_VOLUME_GROUP.NAME))
                .createTimeMs(record.get(FS_VOLUME_GROUP.CREATE_TIME_MS))
                .createUser(record.get(FS_VOLUME_GROUP.CREATE_USER))
                .updateTimeMs(record.get(FS_VOLUME_GROUP.UPDATE_TIME_MS))
                .updateUser(record.get(FS_VOLUME_GROUP.UPDATE_USER))
                .build();
    }

    private FsVolume recordToVolume(final Record record,
                                    final FsVolumeState fileSystemVolumeState,
                                    final FsVolumeGroup fsVolumeGroup) {
        final FsVolume.Builder builder = FsVolume
                .builder()
                .id(record.get(FS_VOLUME.ID))
                .version(record.get(FS_VOLUME.VERSION))
                .createTimeMs(record.get(FS_VOLUME.CREATE_TIME_MS))
                .createUser(record.get(FS_VOLUME.CREATE_USER))
                .updateTimeMs(record.get(FS_VOLUME.UPDATE_TIME_MS))
                .updateUser(record.get(FS_VOLUME.UPDATE_USER))
                .path(record.get(FS_VOLUME.PATH))
                .status(VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(FS_VOLUME.STATUS)))
                .byteLimit(record.get(FS_VOLUME.BYTE_LIMIT))
                .volumeState(fileSystemVolumeState)
                .volumeType(FsVolumeType.fromId(record.get(FS_VOLUME.VOLUME_TYPE)))
                .volumeGroup(fsVolumeGroup);

        final byte[] data = record.get(FS_VOLUME.DATA);
        if (data != null) {
            try {
                final String s3ClientConfigData = new String(data, StandardCharsets.UTF_8);
                builder.s3ClientConfigData(s3ClientConfigData);
                final S3ClientConfig s3ClientConfig = JsonUtil
                        .readValue(s3ClientConfigData, S3ClientConfig.class);
                builder.s3ClientConfig(s3ClientConfig);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return builder.build();
    }

    private Optional<Condition> volumeStatusCriteriaSetToCondition(final TableField<FsVolumeRecord, Byte> field,
                                                                   final Selection<VolumeUseStatus> selection) {
        final Selection<Byte> set = Selection.selectNone();
        set.setMatchAll(selection.isMatchAll());
        set.setSet(selection.getSet().stream().map(VolumeUseStatus::getPrimitiveValue).collect(Collectors.toSet()));
        return JooqUtil.getSetCondition(field, set);
    }


    // --------------------------------------------------------------------------------


    /// Temporary local cache of the parent table objects
    private record Caches(Int2ObjectMap<FsVolumeGroup> volGrpCache) {

        private Caches() {
            this(new Int2ObjectOpenHashMap<>());
        }
    }
}
