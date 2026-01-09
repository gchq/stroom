/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.data.store.impl.fs.s3v2.FileKey;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryKey;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryTask;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryTaskDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.jooq.Name;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.data.store.impl.fs.db.jooq.tables.FsMetaVolume.FS_META_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.ZstdDictionaryTask.ZSTD_DICTIONARY_TASK;

public class ZstdDictionaryTaskDaoImpl implements ZstdDictionaryTaskDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdDictionaryTaskDaoImpl.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    ZstdDictionaryTaskDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    public ZstdDictionaryTask create(final ZstdDictionaryKey zstdDictionaryKey,
                                     final FileKey fileKey) {
        Objects.requireNonNull(zstdDictionaryKey);
        Objects.requireNonNull(fileKey);
        final long createTimeMs = System.currentTimeMillis();

        final long id = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                context.insertInto(
                                ZSTD_DICTIONARY_TASK,
                                ZSTD_DICTIONARY_TASK.ID,
                                ZSTD_DICTIONARY_TASK.CREATE_TIME_MS,
                                ZSTD_DICTIONARY_TASK.META_ID,
                                ZSTD_DICTIONARY_TASK.FEED_NAME,
                                ZSTD_DICTIONARY_TASK.STREAM_TYPE_NAME,
                                ZSTD_DICTIONARY_TASK.CHILD_STREAM_TYPE_NAME)
                        .values(
                                null,
                                createTimeMs,
                                fileKey.metaId(),
                                zstdDictionaryKey.feedName(),
                                zstdDictionaryKey.streamTypeName(),
                                zstdDictionaryKey.childStreamType())
                        .returningResult(ZSTD_DICTIONARY_TASK.ID)
                        .fetchOne(ZSTD_DICTIONARY_TASK.ID));

        final ZstdDictionaryTask zstdDictionaryTask = new ZstdDictionaryTask(
                id,
                createTimeMs,
                zstdDictionaryKey,
                fileKey.metaId(),
                fileKey.volumeId());
        LOGGER.debug("create() - dictionaryTask: {}", zstdDictionaryTask);
        return zstdDictionaryTask;
    }

    @Override
    public void delete(final Collection<ZstdDictionaryTask> dictionaryTasks) {
        Objects.requireNonNull(dictionaryTasks);
        final Set<Long> ids = dictionaryTasks.stream()
                .map(ZstdDictionaryTask::id)
                .collect(Collectors.toSet());
        LOGGER.debug(() -> LogUtil.message("delete() - ids count: {}", ids.size()));

        final Integer count = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                context.deleteFrom(ZSTD_DICTIONARY_TASK)
                        .where(ZSTD_DICTIONARY_TASK.ID.in(ids))
                        .execute());

        LOGGER.debug(() -> LogUtil.message("delete() - ids count: {}, count: {}", ids.size(), count));
    }

    @Override
    public int deleteByMetaIds(final Collection<Long> metaIds) {
        final int count;
        if (NullSafe.hasItems(metaIds)) {
            LOGGER.debug(() -> LogUtil.message("deleteByMetaIds() - metaIds count: {}", metaIds.size()));

            count = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                    context.deleteFrom(ZSTD_DICTIONARY_TASK)
                            .where(ZSTD_DICTIONARY_TASK.META_ID.in(metaIds))
                            .execute());

            LOGGER.debug(() -> LogUtil.message("deleteByMetaIds() - metaIds count: {}, count: {}",
                    metaIds.size(), count));
        } else {
            count = 0;
        }
        return count;
    }

    @Override
    public Map<ZstdDictionaryKey, ZstdDictionaryTask> fetchTasks(final int limit) {
        // TODO It uses a sub-select because there is no unique key on the meta_id col on fs_meta_volume
        //  and the query uses a limit which would complicate using selectDistinct with a join.
        final Map<ZstdDictionaryKey, ZstdDictionaryTask> dictionaryTaskMap = new HashMap<>();
        final Name volIdFieldName = DSL.name("fs_vol_id");
        // Order by feed, strmType, childStrmType, createTime so we hopefully get all or most rows for a
        // dictionary key so we will have enough samples to make a dict from.
        JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                        context.select(ZSTD_DICTIONARY_TASK.ID,
                                        ZSTD_DICTIONARY_TASK.CREATE_TIME_MS,
                                        ZSTD_DICTIONARY_TASK.FEED_NAME,
                                        ZSTD_DICTIONARY_TASK.STREAM_TYPE_NAME,
                                        ZSTD_DICTIONARY_TASK.CHILD_STREAM_TYPE_NAME,
                                        ZSTD_DICTIONARY_TASK.META_ID,
                                        DSL.field(DSL.select(FS_META_VOLUME.FS_VOLUME_ID)
                                                        .from(FS_META_VOLUME)
                                                        .where(FS_META_VOLUME.META_ID.eq(ZSTD_DICTIONARY_TASK.META_ID)))
                                                .as(volIdFieldName))
                                .from(ZSTD_DICTIONARY_TASK)
                                .where()
                                .orderBy(
                                        ZSTD_DICTIONARY_TASK.FEED_NAME,
                                        ZSTD_DICTIONARY_TASK.STREAM_TYPE_NAME,
                                        ZSTD_DICTIONARY_TASK.CHILD_STREAM_TYPE_NAME,
                                        ZSTD_DICTIONARY_TASK.CREATE_TIME_MS)
                                .limit(limit)
                                .fetch())
                .forEach(rec -> {
                    final Integer volId = rec.get(volIdFieldName, int.class);
                    if (volId != null) {
                        final ZstdDictionaryKey zstdDictionaryKey = ZstdDictionaryKey.of(
                                rec.get(ZSTD_DICTIONARY_TASK.FEED_NAME),
                                rec.get(ZSTD_DICTIONARY_TASK.STREAM_TYPE_NAME),
                                rec.get(ZSTD_DICTIONARY_TASK.CHILD_STREAM_TYPE_NAME));
                        final ZstdDictionaryTask zstdDictionaryTask = new ZstdDictionaryTask(
                                rec.get(ZSTD_DICTIONARY_TASK.ID),
                                rec.get(ZSTD_DICTIONARY_TASK.CREATE_TIME_MS),
                                zstdDictionaryKey,
                                rec.get(ZSTD_DICTIONARY_TASK.META_ID),
                                volId);
                        dictionaryTaskMap.put(zstdDictionaryKey, zstdDictionaryTask);
                    } else {
                        LOGGER.debug(() -> LogUtil.message("fetchTasks() - Null volId for metaId: {}",
                                rec.get(ZSTD_DICTIONARY_TASK.META_ID)));
                    }
                });
        return dictionaryTaskMap;
    }
}
