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


import stroom.data.store.impl.fs.db.jooq.tables.records.ZstdDictionaryRecord;
import stroom.data.store.impl.fs.s3v2.LinkedZstdDictionary;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryDao;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryKey;
import stroom.data.store.impl.fs.s3v2.ZstdDictionaryStatus;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.Objects;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.ZstdDictionary.ZSTD_DICTIONARY;

public class ZstdDictionaryDaoImpl implements ZstdDictionaryDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdDictionaryDaoImpl.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final GenericDao<ZstdDictionaryRecord, LinkedZstdDictionary, Integer> genericDao;

    @Inject
    ZstdDictionaryDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        this.genericDao = new GenericDao<>(
                fsDataStoreDbConnProvider,
                ZSTD_DICTIONARY,
                ZSTD_DICTIONARY.ID,
                ZstdDictionaryDaoImpl::mapToRecord,
                ZstdDictionaryDaoImpl::mapFromRecord);
    }

    @Override
    public Optional<LinkedZstdDictionary> fetchByKey(final ZstdDictionaryKey zstdDictionaryKey) {
        Objects.requireNonNull(zstdDictionaryKey);
        // Get the most recent record for the given zstdDictionaryKey
        final Optional<LinkedZstdDictionary> optDict = JooqUtil.contextResult(
                        fsDataStoreDbConnProvider, context ->
                                context.select(DSL.asterisk())
                                        .from(ZSTD_DICTIONARY)
                                        .where(ZSTD_DICTIONARY.FEED_NAME.eq(
                                                zstdDictionaryKey.feedName()))
                                        .and(ZSTD_DICTIONARY.STREAM_TYPE_NAME.eq(
                                                zstdDictionaryKey.streamTypeName()))
                                        .and(ZSTD_DICTIONARY.CHILD_STREAM_TYPE_NAME.eq(
                                                zstdDictionaryKey.childStreamType()))
                                        .and(ZSTD_DICTIONARY.STATUS.eq(
                                                ZstdDictionaryStatus.TRAINED.getPrimitiveValue()))
                                        .orderBy(ZSTD_DICTIONARY.CREATE_TIME_MS.desc())
                                        .limit(1)
                                        .fetchOptional())
                .map(ZstdDictionaryDaoImpl::mapFromRecord);

        LOGGER.debug("fetchByKey() - zstdDictionaryKey: {}, optDict: {}", zstdDictionaryKey, optDict);
        return optDict;
    }

//    @Override
//    public Optional<LinkedZstdDictionary> fetchByUuid(final String uuid) {
//        if (uuid == null) {
//            return Optional.empty();
//        } else {
//            final Optional<LinkedZstdDictionary> optDict = JooqUtil.contextResult(
//                            fsDataStoreDbConnProvider, context ->
//                                    context.select(DSL.asterisk())
//                                            .from(ZSTD_DICTIONARY)
//                                            .where(ZSTD_DICTIONARY.UUID.eq(uuid))
//                                            .fetchOptional())
//                    .map(RECORD_TO_DICT_MAPPER);
//            LOGGER.debug("fetchByUuid() - uuid: {}, optDict: {}", uuid, optDict);
//            return optDict;
//        }
//    }

    @Override
    public LinkedZstdDictionary create(final LinkedZstdDictionary linkedZstdDictionary) {
        return genericDao.create(linkedZstdDictionary);
    }

    @Override
    public void updateStatus(final String uuid, final ZstdDictionaryStatus status) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(status);
        final int count = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                context.update(ZSTD_DICTIONARY)
                        .set(ZSTD_DICTIONARY.STATUS, status.getPrimitiveValue())
                        .where(ZSTD_DICTIONARY.UUID.eq(uuid))
                        .execute());
        LOGGER.debug("updateStatus() - uuid: {}, status: {}, count: {}", uuid, status, count);
    }

    private static LinkedZstdDictionary mapFromRecord(final Record record) {
        return new LinkedZstdDictionary(
                record.get(ZSTD_DICTIONARY.ID),
                record.get(ZSTD_DICTIONARY.VERSION),
                record.get(ZSTD_DICTIONARY.CREATE_TIME_MS),
                record.get(ZSTD_DICTIONARY.CREATE_USER),
                record.get(ZSTD_DICTIONARY.UPDATE_TIME_MS),
                record.get(ZSTD_DICTIONARY.UPDATE_USER),
                record.get(ZSTD_DICTIONARY.FEED_NAME),
                record.get(ZSTD_DICTIONARY.STREAM_TYPE_NAME),
                record.get(ZSTD_DICTIONARY.CHILD_STREAM_TYPE_NAME),
//                    record.get(ZSTD_DICTIONARY.DICTIONARY_VERSION),
                record.get(ZSTD_DICTIONARY.UUID),
                ZstdDictionaryStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                        record.get(ZSTD_DICTIONARY.STATUS)));
    }

    private static ZstdDictionaryRecord mapToRecord(final LinkedZstdDictionary linkedZstdDictionary,
                                                    final ZstdDictionaryRecord record) {
        record.from(linkedZstdDictionary);
        record.set(ZSTD_DICTIONARY.ID, linkedZstdDictionary.getId());
        record.set(ZSTD_DICTIONARY.VERSION, linkedZstdDictionary.getVersion());
        record.set(ZSTD_DICTIONARY.CREATE_TIME_MS, linkedZstdDictionary.getCreateTimeMs());
        record.set(ZSTD_DICTIONARY.CREATE_USER, linkedZstdDictionary.getCreateUser());
        record.set(ZSTD_DICTIONARY.UPDATE_TIME_MS, linkedZstdDictionary.getUpdateTimeMs());
        record.set(ZSTD_DICTIONARY.UPDATE_USER, linkedZstdDictionary.getUpdateUser());
        record.set(ZSTD_DICTIONARY.FEED_NAME, linkedZstdDictionary.getFeedName());
        record.set(ZSTD_DICTIONARY.STREAM_TYPE_NAME, linkedZstdDictionary.getStreamTypeName());
        record.set(ZSTD_DICTIONARY.CHILD_STREAM_TYPE_NAME, linkedZstdDictionary.getChildStreamType());
//        record.set(ZSTD_DICTIONARY.DICTIONARY_VERSION, linkedZstdDictionary.getDictionaryVersion());
        record.set(ZSTD_DICTIONARY.UUID, linkedZstdDictionary.getUuid());
        record.set(ZSTD_DICTIONARY.STATUS, linkedZstdDictionary.getStatus().getPrimitiveValue());
        return record;
    }
}
