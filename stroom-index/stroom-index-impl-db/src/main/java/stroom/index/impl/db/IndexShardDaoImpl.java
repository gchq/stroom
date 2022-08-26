package stroom.index.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.impl.db.jooq.tables.records.IndexShardRecord;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static stroom.index.impl.db.jooq.Tables.INDEX_SHARD;
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

    private static final Map<String, Field<?>> FIELD_MAP = new HashMap<>();

//    private static final Map<String, HasCapacitySelector> VOLUME_SELECTOR_MAP;
//    private static final HasCapacitySelector DEFAULT_VOLUME_SELECTOR;

    static {
        FIELD_MAP.put(FindIndexShardCriteria.FIELD_ID, INDEX_SHARD.ID);
        FIELD_MAP.put(FindIndexShardCriteria.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME);

//        VOLUME_SELECTOR_MAP = Stream.of(
//                        new MostFreePercentCapacitySelector(),
//                        new MostFreeCapacitySelector(),
//                        new RandomCapacitySelector(),
//                        new RoundRobinIgnoreLeastFreePercentCapacitySelector(),
//                        new RoundRobinIgnoreLeastFreeCapacitySelector(),
//                        new RoundRobinCapacitySelector(),
//                        new WeightedFreePercentRandomCapacitySelector(),
//                        new WeightedFreeRandomCapacitySelector()
//                )
//                .collect(Collectors.toMap(HasCapacitySelector::getName, Function.identity()));
//        DEFAULT_VOLUME_SELECTOR = VOLUME_SELECTOR_MAP.get(RoundRobinCapacitySelector.NAME);
    }

    private final IndexDbConnProvider indexDbConnProvider;
    private final IndexVolumeDao indexVolumeDao;
    private final IndexVolumeGroupService indexVolumeGroupService;
    private final Provider<VolumeConfig> volumeConfigProvider;
    private final GenericDao<IndexShardRecord, IndexShard, Long> genericDao;

    @Inject
    IndexShardDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                      final IndexVolumeDao indexVolumeDao,
                      final IndexVolumeGroupService indexVolumeGroupService,
                      final Provider<VolumeConfig> volumeConfigProvider) {
        this.indexDbConnProvider = indexDbConnProvider;
        this.indexVolumeDao = indexVolumeDao;
        this.indexVolumeGroupService = indexVolumeGroupService;
        this.volumeConfigProvider = volumeConfigProvider;
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
                JooqUtil.getStringCondition(INDEX_SHARD.PARTITION_NAME, criteria.getPartition())
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
    public IndexShard create(final IndexShardKey indexShardKey,
                             final IndexVolume indexVolume,
                             final String ownerNodeName,
                             final String indexVersion) {
        // TODO : @66 Add some caching here. Maybe do this as part of volume selection.
//        List<IndexVolume> indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(volumeGroupName, ownerNodeName);
//
//        if (indexVolumes.size() == 0) {
//            //Could be due to default volume groups not having been created - but this will force as side effect
//            final List<String> groupNames = indexVolumeGroupService.getNames();
//
//            // Now re-fetch
//            indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(volumeGroupName, ownerNodeName);
//
//            //Check again.
//            if (indexVolumes == null || indexVolumes.size() == 0) {
//                throw new IndexException("Unable to find any index volumes for group with name " + volumeGroupName +
//                        ((groupNames == null || groupNames.size() == 0)
//                                ? " No index groups defined."
//                                :
//                                        " Available index volume groups: " + String.join(", ", groupNames)));
//            }
//        }
//
//        indexVolumes = VolumeListUtil.removeFullVolumes(indexVolumes);
//
//        final HasCapacitySelector volumeSelector = getVolumeSelector();
//        final IndexVolume indexVolume = volumeSelector.select(indexVolumes);
//        if (indexVolume == null) {
//            final String msg = "No shard can be created as no volumes are available for group: " +
//                    volumeGroupName +
//                    " indexUuid: " +
//                    indexShardKey.getIndexUuid();
//            throw new IndexException(msg);
//        }

        final IndexShard indexShard = new IndexShard();
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setNodeName(ownerNodeName);
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
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

//    private HasCapacitySelector getVolumeSelector() {
//        HasCapacitySelector volumeSelector = null;
//
//        try {
//            final String value = volumeConfigProvider.get().getVolumeSelector();
//            if (value != null) {
//                volumeSelector = VOLUME_SELECTOR_MAP.get(value);
//            }
//        } catch (final RuntimeException e) {
//            LOGGER.debug(e::getMessage);
//        }
//
//        if (volumeSelector == null) {
//            volumeSelector = DEFAULT_VOLUME_SELECTOR;
//        }
//
//        return volumeSelector;
//    }
}
