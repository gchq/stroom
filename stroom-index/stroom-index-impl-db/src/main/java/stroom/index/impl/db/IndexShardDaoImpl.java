package stroom.index.impl.db;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapper.Converter;
import stroom.db.util.ExpressionMapper.MultiConverter;
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
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardFields;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.Partition;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.index.impl.db.jooq.Tables.INDEX_SHARD;
import static stroom.index.impl.db.jooq.Tables.INDEX_VOLUME_GROUP;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;

@Singleton // holding all the volume selectors
class IndexShardDaoImpl implements IndexShardDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardDaoImpl.class);

    private static final Function<Record, IndexShard> RECORD_TO_INDEX_SHARD_MAPPER = record -> {
        final IndexShard indexShard = new IndexShard();
        indexShard.setId(record.get(INDEX_SHARD.ID));
        indexShard.setPartition(record.get(INDEX_SHARD.PARTITION_NAME));
        indexShard.setPartitionFromTime(record.get(INDEX_SHARD.PARTITION_FROM_MS));
        indexShard.setPartitionToTime(record.get(INDEX_SHARD.PARTITION_TO_MS));
        indexShard.setDocumentCount(record.get(INDEX_SHARD.DOCUMENT_COUNT));
        indexShard.setCommitMs(record.get(INDEX_SHARD.COMMIT_MS));
        indexShard.setCommitDurationMs(record.get(INDEX_SHARD.COMMIT_DURATION_MS));
        indexShard.setCommitDocumentCount(record.get(INDEX_SHARD.COMMIT_DOCUMENT_COUNT));
        indexShard.setStatus(IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                record.get(INDEX_SHARD.STATUS)));
        indexShard.setFileSize(record.get(INDEX_SHARD.FILE_SIZE));
        indexShard.setIndexVersion(record.get(INDEX_SHARD.INDEX_VERSION));
        indexShard.setNodeName(record.get(INDEX_SHARD.NODE_NAME));
        indexShard.setIndexUuid(record.get(INDEX_SHARD.INDEX_UUID));
        return indexShard;
    };

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
            FindIndexShardCriteria.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME);

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
                    final IndexShard indexShard = RECORD_TO_INDEX_SHARD_MAPPER.apply(r);
                    indexShard.setVolume(indexVolume);
                    return indexShard;
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
                        .map(INDEX_SHARD.PARTITION_TO_MS::lessThan)
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
                    final IndexShard indexShard = RECORD_TO_INDEX_SHARD_MAPPER.apply(r);
                    indexShard.setVolume(indexVolume);
                    return indexShard;
                });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final ValuesConsumer consumer) {

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final List<AbstractField> fieldList = Arrays.asList(fields);
        final List<Field<?>> dbFields = new ArrayList<>(indexShardValueMapper.getFields(fieldList));
        final Mapper<?>[] mappers = indexShardValueMapper.getMappers(fields);
        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = indexShardExpressionMapper.apply(criteria.getExpression());

        final boolean volumeUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_PATH, IndexShardFields.FIELD_VOLUME_GROUP),
                fieldList,
                criteria);
        final boolean volumeGroupUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_GROUP),
                fieldList,
                criteria);

        JooqUtil.context(indexDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            var select = context
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
                        final Val[] arr = new Val[fields.length];
                        for (int i = 0; i < fields.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.add(arr);
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
        final IndexShard indexShard = new IndexShard();
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setNodeName(ownerNodeName);
        indexShard.setPartition(partition.getLabel());
        indexShard.setPartitionFromTime(partition.getPartitionFromTime());
        indexShard.setPartitionToTime(partition.getPartitionToTime());
        indexShard.setVolume(indexVolume);
        indexShard.setIndexVersion(indexVersion);

        final IndexShard created = genericDao.create(indexShard);
        created.setVolume(indexVolume);

        return created;
    }

    @Override
    public void delete(final Long id) {
        genericDao.delete(id);
    }

    @Override
    public void setStatus(final Long id,
                          final IndexShard.IndexShardStatus status) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, status.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
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

    private boolean isUsed(final Set<AbstractField> fieldSet,
                           final List<AbstractField> resultFields,
                           final ExpressionCriteria criteria) {
        return resultFields.stream().filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
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
            valueMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, ValInteger::create);
            valueMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, this::getStatus);
            valueMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, ValString::create);
        }

        private Val getDocRefName(final String uuid) {
            String val = uuid;
            if (docRefInfoService != null) {
                val = docRefInfoService.name(new DocRef(IndexDoc.DOCUMENT_TYPE, uuid))
                        .orElse(uuid);
            }
            return ValString.create(val);
        }

        private Val getStatus(final byte statusPrimitive) {
            final IndexShardStatus indexShardStatus = IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                    statusPrimitive);
            return ValString.create(indexShardStatus.getDisplayValue());
        }

        public <T> void map(final AbstractField dataSourceField,
                            final Field<T> field,
                            final Function<T, Val> handler) {
            valueMapper.map(dataSourceField, field, handler);
        }

        public List<Field<?>> getFields(final List<AbstractField> fields) {
            return valueMapper.getFields(fields);
        }

        public Mapper<?>[] getMappers(final AbstractField[] fields) {
            return valueMapper.getMappers(fields);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Singleton
    private static class IndexShardExpressionMapper {

        private final ExpressionMapper expressionMapper;
        private final IndexStore indexStore;

        private final Map<String, List<String>> indexNameToUuidsMap = new ConcurrentHashMap<>();

        @Inject
        private IndexShardExpressionMapper(final ExpressionMapperFactory expressionMapperFactory,
                                           final IndexStore indexStore) {
            this.indexStore = indexStore;

            this.expressionMapper = expressionMapperFactory.create();
            expressionMapper.map(IndexShardFields.FIELD_NODE, INDEX_SHARD.NODE_NAME, value -> value);
            expressionMapper.multiMap(
                    IndexShardFields.FIELD_INDEX, INDEX_SHARD.INDEX_UUID, this::getIndexUuids, true);
            expressionMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, Integer::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, value ->
                    IndexShardStatus.fromDisplayValue(value).getPrimitiveValue());
            expressionMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, value -> value);
        }

        private List<String> getIndexUuids(final List<String> indexNames) {
            return indexStore.findByNames(indexNames, true)
                    .stream()
                    .map(DocRef::getUuid)
                    .collect(Collectors.toList());
        }

        public <T> void map(final AbstractField dataSourceField,
                            final Field<T> field,
                            final Converter<T> converter) {
            expressionMapper.map(dataSourceField, field, converter);
        }

        public <T> void map(final AbstractField dataSourceField,
                            final Field<T> field,
                            final Converter<T> converter, final boolean useName) {
            expressionMapper.map(dataSourceField, field, converter, useName);
        }

        public <T> void multiMap(final AbstractField dataSourceField,
                                 final Field<T> field,
                                 final MultiConverter<T> converter) {
            expressionMapper.multiMap(dataSourceField, field, converter);
        }

        public <T> void multiMap(final AbstractField dataSourceField,
                                 final Field<T> field,
                                 final MultiConverter<T> converter, final boolean useName) {
            expressionMapper.multiMap(dataSourceField, field, converter, useName);
        }

        public void ignoreField(final AbstractField dataSourceField) {
            expressionMapper.ignoreField(dataSourceField);
        }

        public Condition apply(final ExpressionItem expressionItem) {
            return expressionMapper.apply(expressionItem);
        }
    }
}
