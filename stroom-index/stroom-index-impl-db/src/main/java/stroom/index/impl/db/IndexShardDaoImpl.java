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

package stroom.index.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexStore;
import stroom.index.impl.db.jooq.tables.records.IndexShardRecord;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardFields;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.Partition;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;
import static stroom.index.impl.db.jooq.tables.IndexVolumeGroup.INDEX_VOLUME_GROUP;

@Singleton // holding all the volume selectors
class IndexShardDaoImpl implements IndexShardDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardDaoImpl.class);

    private static final Function<Record, IndexShard> RECORD_TO_INDEX_SHARD_MAPPER = record -> IndexShard
            .builder()
            .id(record.get(INDEX_SHARD.ID))
            .partition(record.get(INDEX_SHARD.PARTITION_NAME))
            .partitionFromTime(record.get(INDEX_SHARD.PARTITION_FROM_MS))
            .partitionToTime(record.get(INDEX_SHARD.PARTITION_TO_MS))
            .documentCount(record.get(INDEX_SHARD.DOCUMENT_COUNT))
            .commitMs(record.get(INDEX_SHARD.COMMIT_MS))
            .commitDurationMs(record.get(INDEX_SHARD.COMMIT_DURATION_MS))
            .commitDocumentCount(record.get(INDEX_SHARD.COMMIT_DOCUMENT_COUNT))
            .status(IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
            record.get(INDEX_SHARD.STATUS)))
            .fileSize(record.get(INDEX_SHARD.FILE_SIZE))
            .indexVersion(record.get(INDEX_SHARD.INDEX_VERSION))
            .nodeName(record.get(INDEX_SHARD.NODE_NAME))
            .indexUuid(record.get(INDEX_SHARD.INDEX_UUID))
            .build();

    private static final BiFunction<IndexShard, IndexShardRecord, IndexShardRecord> INDEX_SHARD_TO_RECORD_MAPPER =
            (indexShard, record) -> {
                record.from(indexShard);
                record.set(INDEX_SHARD.ID, indexShard.getId());
                record.set(INDEX_SHARD.PARTITION_NAME, indexShard.getPartition());
                record.set(INDEX_SHARD.PARTITION_FROM_MS, indexShard.getPartitionFromTime());
                record.set(INDEX_SHARD.PARTITION_TO_MS, indexShard.getPartitionToTime());
                record.set(INDEX_SHARD.DOCUMENT_COUNT, indexShard.getDocumentCount());
                record.set(INDEX_SHARD.COMMIT_MS, indexShard.getCommitMs());
                record.set(INDEX_SHARD.COMMIT_DURATION_MS, indexShard.getCommitDurationMs());
                record.set(INDEX_SHARD.COMMIT_DOCUMENT_COUNT, indexShard.getCommitDocumentCount());
                record.set(INDEX_SHARD.STATUS, indexShard.getStatus().getPrimitiveValue());
                record.set(INDEX_SHARD.FILE_SIZE, indexShard.getFileSize());
                record.set(INDEX_SHARD.INDEX_VERSION, indexShard.getIndexVersion());
                record.set(INDEX_SHARD.FK_VOLUME_ID, indexShard.getVolume().getId());
                record.set(INDEX_SHARD.NODE_NAME, indexShard.getNodeName());
                record.set(INDEX_SHARD.INDEX_UUID, indexShard.getIndexUuid());
                return record;
            };

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindIndexShardCriteria.FIELD_ID, INDEX_SHARD.ID,
            FindIndexShardCriteria.FIELD_NODE, INDEX_SHARD.NODE_NAME,
            FindIndexShardCriteria.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME,
            FindIndexShardCriteria.FIELD_STATUS, INDEX_SHARD.STATUS,
            FindIndexShardCriteria.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT,
            FindIndexShardCriteria.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS,
            FindIndexShardCriteria.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE);

    private final IndexDbConnProvider indexDbConnProvider;
    private final GenericDao<IndexShardRecord, IndexShard, Long> genericDao;
    private final IndexShardValueMapper indexShardValueMapper;
    private final IndexShardExpressionMapper indexShardExpressionMapper;

    @Inject
    IndexShardDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                      final IndexShardValueMapper indexShardValueMapper,
                      final IndexShardExpressionMapper indexShardExpressionMapper) {

        this.indexDbConnProvider = indexDbConnProvider;
        this.indexShardValueMapper = indexShardValueMapper;
        this.indexShardExpressionMapper = indexShardExpressionMapper;
        genericDao = new GenericDao<>(
                indexDbConnProvider,
                INDEX_SHARD,
                INDEX_SHARD.ID,
                INDEX_SHARD_TO_RECORD_MAPPER,
                RECORD_TO_INDEX_SHARD_MAPPER);
    }

    @Override
    public Optional<IndexShard> fetch(final long id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_SHARD)
                        .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                        .where(INDEX_SHARD.ID.eq(id))
                        .fetchOptional())
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    return RECORD_TO_INDEX_SHARD_MAPPER.apply(r).copy().volume(indexVolume).build();
                });
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getRangeCondition(INDEX_SHARD.DOCUMENT_COUNT, criteria.getDocumentCountRange()),
                JooqUtil.getSetCondition(INDEX_SHARD.NODE_NAME, criteria.getNodeNameSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.FK_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.ID, criteria.getIndexShardIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.INDEX_UUID, criteria.getIndexUuidSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.STATUS,
                        Selection.convert(criteria.getIndexShardStatusSet(),
                                IndexShard.IndexShardStatus::getPrimitiveValue)),
                JooqUtil.getStringCondition(INDEX_SHARD.PARTITION_NAME, criteria.getPartition()),
                Optional.ofNullable(criteria.getPartitionTimeRange())
                        .map(Range::getFrom)
                        .map(INDEX_SHARD.PARTITION_FROM_MS::greaterOrEqual),
                Optional.ofNullable(criteria.getPartitionTimeRange())
                        .map(Range::getTo)
                        .map(INDEX_SHARD.PARTITION_TO_MS::lessOrEqual)
        );

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<IndexShard> list = JooqUtil.contextResult(indexDbConnProvider, context ->
                        context
                                .select()
                                .from(INDEX_SHARD)
                                .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                                .where(conditions)
                                .orderBy(orderFields)
                                .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                                .fetch())
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    return RECORD_TO_INDEX_SHARD_MAPPER.apply(r).copy().volume(indexVolume).build();
                });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final String[] fieldNames = fieldIndex.getFields();
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final List<Field<?>> dbFields = indexShardValueMapper.getDbFieldsByName(fieldNames);
        final Mapper<?>[] mappers = indexShardValueMapper.getMappersForFieldNames(fieldNames);
        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = indexShardExpressionMapper.apply(criteria.getExpression());

        final boolean volumeUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_PATH.getFldName(),
                        IndexShardFields.FIELD_VOLUME_GROUP.getFldName()),
                fieldNames,
                criteria);
        final boolean volumeGroupUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_GROUP.getFldName()),
                fieldNames,
                criteria);

        JooqUtil.context(indexDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            SelectJoinStep<Record> select = context
                    .select(dbFields)
                    .from(INDEX_SHARD);

            if (volumeUsed) {
                select = select.join(INDEX_VOLUME)
                        .on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID));
            }

            if (volumeGroupUsed) {
                select = select.join(INDEX_VOLUME_GROUP)
                        .on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);

                    result.forEach(r -> {
                        final Val[] arr = new Val[fieldNames.length];
                        for (int i = 0; i < fieldNames.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.accept(Val.of(arr));
                    });
                }
            }
        });
    }

    @Override
    public IndexShard create(final IndexShardKey indexShardKey,
                             final IndexVolume indexVolume,
                             final String ownerNodeName,
                             final String indexVersion) {
        final Partition partition = indexShardKey.getPartition();
        final IndexShard indexShard = IndexShard
                .builder()
                .indexUuid(indexShardKey.getIndexUuid())
                .nodeName(ownerNodeName)
                .partition(partition.getLabel())
                .partitionFromTime(partition.getPartitionFromTime())
                .partitionToTime(partition.getPartitionToTime())
                .volume(indexVolume)
                .indexVersion(indexVersion)
                .build();

        return genericDao.create(indexShard).copy().volume(indexVolume).build();
    }

    @Override
    public boolean delete(final Long id) {
        return genericDao.delete(id);
    }

    @Override
    public boolean setStatus(final Long id,
                             final IndexShardStatus status) {
        final Condition currentStateCondition = switch (status) {
            case NEW -> DSL.falseCondition();
            case OPENING -> INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSED.getPrimitiveValue())
                    .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.NEW.getPrimitiveValue()));
            case OPEN -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPENING.getPrimitiveValue());
            case CLOSING -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPEN.getPrimitiveValue());
            case CLOSED -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPENING.getPrimitiveValue())
                    .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSING.getPrimitiveValue()));
            case DELETED -> DSL.trueCondition();
            case CORRUPT -> INDEX_SHARD.STATUS.ne(IndexShardStatus.DELETED.getPrimitiveValue());
        };

        final boolean didUpdate = JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, status.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
                .and(currentStateCondition)
                .execute()) > 0;

        if (LOGGER.isDebugEnabled()) {
            if (didUpdate) {
                LOGGER.debug("Set shard status to {} for shard id {}", status, id);
            } else {
                try {
                    final Optional<IndexShardStatus> optStatus = fetch(id)
                            .map(IndexShard::getStatus);
                    LOGGER.debug("Unable to update status to {} for shard id {}, optStatus: {}", status, id, optStatus);
                } catch (final Exception e) {
                    LOGGER.debug("Error trying to fetch shard for debug", e);
                }
            }
        }

        return didUpdate;
    }

    @Override
    public void logicalDelete(final Long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, IndexShardStatus.DELETED.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
                .execute());
    }

    @Override
    public void reset(final Long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, IndexShardStatus.CLOSED.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.STATUS.eq(IndexShardStatus.OPENING.getPrimitiveValue())
                        .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.OPEN.getPrimitiveValue()))
                        .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSING.getPrimitiveValue()))
                )
                .execute());
    }

    @Override
    public void update(final Long id,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.DOCUMENT_COUNT, documentCount)
                .set(INDEX_SHARD.COMMIT_DOCUMENT_COUNT, 0)
                .set(INDEX_SHARD.COMMIT_DURATION_MS, commitDurationMs)
                .set(INDEX_SHARD.COMMIT_MS, commitMs)
                .set(INDEX_SHARD.FILE_SIZE, fileSize)
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.DOCUMENT_COUNT.ne(documentCount)
                        .or(INDEX_SHARD.FILE_SIZE.ne(fileSize))
                )
                .execute());
    }

    private boolean isUsed(final Set<String> fieldSet,
                           final String[] fields,
                           final ExpressionCriteria criteria) {
        return Arrays.stream(fields).filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
               ExpressionUtil.termCount(criteria.getExpression(), fieldSet) > 0;
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Singleton
    private static class IndexShardValueMapper {

        private final ValueMapper valueMapper;
        private final DocRefInfoService docRefInfoService;

        @Inject
        private IndexShardValueMapper(final ValueMapper valueMapper,
                                      final DocRefInfoService docRefInfoService) {
            this.valueMapper = valueMapper;
            this.docRefInfoService = docRefInfoService;

            valueMapper.map(IndexShardFields.FIELD_NODE, INDEX_SHARD.NODE_NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_INDEX, INDEX_SHARD.INDEX_UUID, this::getDocRefName);
            valueMapper.map(IndexShardFields.FIELD_INDEX_NAME, INDEX_SHARD.INDEX_UUID, this::getDocRefName);
            valueMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, ValInteger::create);
            valueMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, this::getStatus);
            valueMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, ValDate::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_SHARD_ID, INDEX_SHARD.ID, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_INDEX_VERSION, INDEX_SHARD.INDEX_VERSION, ValString::create);
        }

        private Val getDocRefName(final String uuid) {
            String val = uuid;
            if (docRefInfoService != null) {
                val = docRefInfoService.name(new DocRef(LuceneIndexDoc.TYPE, uuid))
                        .orElse(uuid);
            }
            return ValString.create(val);
        }

        private Val getStatus(final byte statusPrimitive) {
            final IndexShardStatus indexShardStatus = IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                    statusPrimitive);
            return ValString.create(indexShardStatus.getDisplayValue());
        }

        public List<Field<?>> getDbFieldsByName(final String[] fieldNames) {
            return valueMapper.getDbFieldsByName(fieldNames);
        }

        public Mapper<?>[] getMappersForFieldNames(final String[] fieldNames) {
            return valueMapper.getMappersForFieldNames(fieldNames);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Singleton
    private static class IndexShardExpressionMapper {

        private final ExpressionMapper expressionMapper;
        private final IndexStore indexStore;

        @Inject
        private IndexShardExpressionMapper(final ExpressionMapperFactory expressionMapperFactory,
                                           final IndexStore indexStore) {
            this.indexStore = indexStore;

            this.expressionMapper = expressionMapperFactory.create();
            expressionMapper.map(IndexShardFields.FIELD_NODE, INDEX_SHARD.NODE_NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_INDEX, INDEX_SHARD.INDEX_UUID, value ->
                    value, false);
            expressionMapper.multiMap(IndexShardFields.FIELD_INDEX_NAME, INDEX_SHARD.INDEX_UUID,
                    this::getIndexUuids, true);
            expressionMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, Integer::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, value ->
                    IndexShardStatus.fromDisplayValue(value).getPrimitiveValue());
            expressionMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, value ->
                    DateExpressionParser.getMs(IndexShardFields.FIELD_LAST_COMMIT.getFldName(), value));
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_SHARD_ID, INDEX_SHARD.ID, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_INDEX_VERSION, INDEX_SHARD.INDEX_VERSION, value -> value);
        }

        private List<String> getIndexUuids(final List<String> indexNames) {
            return indexStore.findByNames(indexNames, true)
                    .stream()
                    .map(DocRef::getUuid)
                    .collect(Collectors.toList());
        }

        public Condition apply(final ExpressionItem expressionItem) {
            return expressionMapper.apply(expressionItem);
        }
    }
}
