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

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeRecord;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.db.util.JooqUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.TableField;

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
    FsVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    @Override
    public FsVolume create(final FsVolume fileVolume) {
        final FsVolumeRecord record = FS_VOLUME.newRecord();
        volumeToRecord(fileVolume, record);
        final FsVolumeRecord persistedRecord = JooqUtil.create(fsDataStoreDbConnProvider, record);
        return recordToVolume(persistedRecord, fileVolume.getVolumeState());
    }

    @Override
    public FsVolume update(final FsVolume fileVolume) {
        final FsVolumeRecord record = FS_VOLUME.newRecord();
        volumeToRecord(fileVolume, record);
        final FsVolumeRecord persistedRecord = JooqUtil.updateWithOptimisticLocking(fsDataStoreDbConnProvider, record);
        final FsVolume result = recordToVolume(persistedRecord, fileVolume.getVolumeState());
        result.setVolumeState(fileVolume.getVolumeState());
        return result;
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
                        .join(FS_VOLUME_STATE)
                        .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
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
                .join(FS_VOLUME_STATE)
                .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                .where(conditions)
                .limit(offset, limit)
                .fetch());

        final List<FsVolume> list = result.map(this::recordToVolume);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public Set<FsVolume> get(final String path) {
        return new HashSet<>(JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .where(FS_VOLUME.PATH.eq(path))
                        .fetch())
                .map(this::recordToVolume));
    }

    @Override
    public List<FsVolume> getAll() {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .leftOuterJoin(FS_VOLUME_STATE).on(FS_VOLUME.FK_FS_VOLUME_STATE_ID.eq(FS_VOLUME_STATE.ID))
                        .fetch())
                .map(this::recordToVolume);
    }

    @Override
    public List<FsVolume> getVolumesInGroup(final String groupName) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE)
                        .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .join(FS_VOLUME_GROUP).on(FS_VOLUME_GROUP.ID.eq(FS_VOLUME.FK_FS_VOLUME_GROUP_ID))
                        .where(FS_VOLUME_GROUP.NAME.eq(groupName))
                        .fetch())
                .map(this::recordToVolume);
    }

    @Override
    public List<FsVolume> getVolumesInGroup(final int groupId) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE)
                        .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .where(FS_VOLUME.FK_FS_VOLUME_GROUP_ID.eq(groupId))
                        .fetch())
                .map(this::recordToVolume);
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
        record.from(fileVolume);
        record.set(FS_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
        record.set(FS_VOLUME.FK_FS_VOLUME_STATE_ID, fileVolume.getVolumeState().getId());
        record.set(FS_VOLUME.VOLUME_TYPE, volumeType.getId());
        record.set(FS_VOLUME.FK_FS_VOLUME_GROUP_ID, fileVolume.getVolumeGroupId());
        record.set(FS_VOLUME.DATA, data);
    }

    private FsVolume recordToVolume(final Record record) {
        final FsVolumeState fileSystemVolumeState = new FsVolumeState(
                record.get(FS_VOLUME_STATE.ID),
                record.get(FS_VOLUME_STATE.VERSION),
                record.get(FS_VOLUME_STATE.BYTES_USED),
                record.get(FS_VOLUME_STATE.BYTES_FREE),
                record.get(FS_VOLUME_STATE.BYTES_TOTAL),
                record.get(FS_VOLUME_STATE.UPDATE_TIME_MS));
        return recordToVolume(record, fileSystemVolumeState);
    }

    private FsVolume recordToVolume(final Record record, final FsVolumeState fileSystemVolumeState) {
        final FsVolume fileVolume = new FsVolume();
        fileVolume.setId(record.get(FS_VOLUME.ID));
        fileVolume.setVersion(record.get(FS_VOLUME.VERSION));
        fileVolume.setCreateTimeMs(record.get(FS_VOLUME.CREATE_TIME_MS));
        fileVolume.setCreateUser(record.get(FS_VOLUME.CREATE_USER));
        fileVolume.setUpdateTimeMs(record.get(FS_VOLUME.UPDATE_TIME_MS));
        fileVolume.setUpdateUser(record.get(FS_VOLUME.UPDATE_USER));
        fileVolume.setPath(record.get(FS_VOLUME.PATH));
        fileVolume.setStatus(
                VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(FS_VOLUME.STATUS)));
        fileVolume.setByteLimit(record.get(FS_VOLUME.BYTE_LIMIT));
        fileVolume.setVolumeState(fileSystemVolumeState);
        fileVolume.setVolumeType(FsVolumeType.fromId(record.get(FS_VOLUME.VOLUME_TYPE)));
        fileVolume.setVolumeGroupId(record.get(FS_VOLUME.FK_FS_VOLUME_GROUP_ID));

        final byte[] data = record.get(FS_VOLUME.DATA);
        if (data != null) {
            try {
                final String s3ClientConfigData = new String(data, StandardCharsets.UTF_8);
                fileVolume.setS3ClientConfigData(s3ClientConfigData);
                final S3ClientConfig s3ClientConfig = JsonUtil
                        .readValue(s3ClientConfigData, S3ClientConfig.class);
                fileVolume.setS3ClientConfig(s3ClientConfig);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return fileVolume;
    }

    private Optional<Condition> volumeStatusCriteriaSetToCondition(final TableField<FsVolumeRecord, Byte> field,
                                                                   final Selection<VolumeUseStatus> selection) {
        final Selection<Byte> set = Selection.selectNone();
        set.setMatchAll(selection.isMatchAll());
        set.setSet(selection.getSet().stream().map(VolumeUseStatus::getPrimitiveValue).collect(Collectors.toSet()));
        return JooqUtil.getSetCondition(field, set);
    }
}
