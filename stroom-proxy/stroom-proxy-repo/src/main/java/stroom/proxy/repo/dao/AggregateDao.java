package stroom.proxy.repo.dao;

import stroom.data.zip.StroomZipFileType;
import stroom.db.util.JooqUtil;
import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.AggregateKey;
import stroom.proxy.repo.RepoDbConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.proxy.repo.queue.WriteQueue;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class AggregateDao {

    private final SqliteJooqHelper jooq;
    private final AtomicLong aggregateId = new AtomicLong();

    private final AtomicLong aggregateNewPosition = new AtomicLong();

    private final RecordQueue recordQueue;
    private final OperationWriteQueue aggregateWriteQueue;
    private final ReadQueue<Aggregate> aggregateReadQueue;

    @Inject
    AggregateDao(final SqliteJooqHelper jooq,
                 final RepoDbConfig dbConfig) {
        this.jooq = jooq;
        init();

        aggregateWriteQueue = new OperationWriteQueue();
        final List<WriteQueue> writeQueues = List.of(aggregateWriteQueue);

        aggregateReadQueue = new ReadQueue<>(this::read, dbConfig.getBatchSize());
        final List<ReadQueue<?>> readQueues = List.of(aggregateReadQueue);

        recordQueue = new RecordQueue(jooq, writeQueues, readQueues, dbConfig.getBatchSize());
    }

    private long read(final long currentReadPos, final long limit, List<Aggregate> readQueue) {
        final AtomicLong pos = new AtomicLong(currentReadPos);
        jooq.readOnlyTransactionResult(context -> context
                        .select(AGGREGATE.ID,
                                AGGREGATE.FEED_NAME,
                                AGGREGATE.TYPE_NAME,
                                AGGREGATE.NEW_POSITION)
                        .from(AGGREGATE)
                        .where(AGGREGATE.NEW_POSITION.isNotNull())
                        .and(AGGREGATE.NEW_POSITION.gt(currentReadPos))
                        .orderBy(AGGREGATE.NEW_POSITION)
                        .limit(limit)
                        .fetch())
                .forEach(r -> {
                    pos.set(r.get(AGGREGATE.NEW_POSITION));
                    final Aggregate aggregate = new Aggregate(
                            r.get(AGGREGATE.ID),
                            r.get(AGGREGATE.FEED_NAME),
                            r.get(AGGREGATE.TYPE_NAME));
                    readQueue.add(aggregate);
                });
        return pos.get();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            aggregateId.set(JooqUtil
                    .getMaxId(context, AGGREGATE, AGGREGATE.ID)
                    .orElse(0L));

            aggregateNewPosition.set(JooqUtil
                    .getMaxId(context, AGGREGATE, AGGREGATE.NEW_POSITION)
                    .orElse(0L));
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, AGGREGATE);
            JooqUtil.checkEmpty(context, AGGREGATE);
        });
        recordQueue.clear();
        init();
    }

//    /**
//     * Close all aggregates that meet the supplied criteria.
//     */
//    public Batch<Aggregate> getClosableAggregates(final int maxItemsPerAggregate,
//                                                  final long maxUncompressedByteSize,
//                                                  final long oldestMs,
//                                                  final long limit) {
//        final Condition condition =
//                AGGREGATE.COMPLETE.eq(false)
//                        .and(
//                                DSL.or(
//                                        AGGREGATE.ITEMS.greaterOrEqual(maxItemsPerAggregate),
//                                        AGGREGATE.BYTE_SIZE.greaterOrEqual(maxUncompressedByteSize),
//                                        AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldestMs)
//                                )
//                        );
//        final List<Aggregate> list = jooq.readOnlyTransactionResult(context -> context
//                        .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
//                        .from(AGGREGATE)
//                        .where(condition)
//                        .orderBy(AGGREGATE.CREATE_TIME_MS)
//                        .limit(limit)
//                        .fetch())
//                .map(r -> new Aggregate(
//                        r.value1(),
//                        r.value2(),
//                        r.value3()));
//        return new Batch<>(list, list.size() == limit);
//    }

    /**
     * Close all aggregates that meet the supplied criteria.
     */
    public synchronized long closeAggregates(final int maxItemsPerAggregate,
                                             final long maxUncompressedByteSize,
                                             final long maxAggregateAgeMs,
                                             final long limit) {
        final long oldestMs = System.currentTimeMillis() - maxAggregateAgeMs;

        final Condition condition =
                AGGREGATE.COMPLETE.eq(false)
                        .and(
                                DSL.or(
                                        AGGREGATE.ITEMS.greaterOrEqual(maxItemsPerAggregate),
                                        AGGREGATE.BYTE_SIZE.greaterOrEqual(maxUncompressedByteSize),
                                        AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldestMs)
                                )
                        );
        final List<Aggregate> list = jooq.readOnlyTransactionResult(context -> context
                        .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                        .from(AGGREGATE)
                        .where(condition)
                        .orderBy(AGGREGATE.CREATE_TIME_MS)
                        .limit(limit)
                        .fetch())
                .map(r -> new Aggregate(
                        r.value1(),
                        r.value2(),
                        r.value3()));

        recordQueue.add(() -> {
            for (final Aggregate aggregate : list) {
                aggregateWriteQueue.add(context -> context
                        .update(AGGREGATE)
                        .set(AGGREGATE.COMPLETE, true)
                        .set(AGGREGATE.NEW_POSITION, aggregateNewPosition.incrementAndGet())
                        .where(AGGREGATE.ID.eq(aggregate.getId()))
                        .execute());
            }
        });

        // Ensure all DB changes are flushed to the db.
        recordQueue.flush();

        return list.size();
    }

    public Batch<Aggregate> getNewAggregates() {
        return recordQueue.getBatch(aggregateReadQueue);
    }

    public Batch<Aggregate> getNewAggregates(final long timeout,
                                             final TimeUnit timeUnit) {
        return recordQueue.getBatch(aggregateReadQueue, timeout, timeUnit);
    }

    public synchronized void addItems(final Batch<RepoSourceItemRef> newSourceItems,
                                      final int maxItemsPerAggregate,
                                      final long maxUncompressedByteSize) {
        if (!newSourceItems.isEmpty()) {
            final Map<AggregateKey, List<RepoSourceItemRef>> itemMap = newSourceItems
                    .list()
                    .stream()
                    .collect(Collectors
                            .groupingBy(item -> new AggregateKey(item.getFeedName(), item.getTypeName())));


            final OperationWriteQueue operationWriteQueue = new OperationWriteQueue();

            AggregateRecord currentRecord = null;

            for (final List<RepoSourceItemRef> items : itemMap.values()) {
                for (final RepoSourceItemRef item : items) {

                    // Get an aggregate to fit the item.
                    if (currentRecord != null) {
                        if (!Objects.equals(currentRecord.getFeedName(), item.getFeedName()) ||
                                !Objects.equals(currentRecord.getTypeName(), item.getTypeName()) ||
                                currentRecord.getItems() >= maxItemsPerAggregate ||
                                (currentRecord.getItems() > 0 &&
                                        currentRecord.getByteSize() + item.getTotalByteSize() >
                                                maxUncompressedByteSize)) {
                            // Commit and nullify current record.
                            final AggregateRecord record = currentRecord;
                            operationWriteQueue.add(context -> context
                                    .batchUpdate(record).execute());
                            currentRecord = null;
                        }
                    }

                    if (currentRecord == null) {
                        // Flush current queue so aggregates are updated.
                        if (operationWriteQueue.size() > 0) {
                            jooq.transaction(operationWriteQueue::flush);
                            operationWriteQueue.clear();
                        }

                        // Try to find an appropriate record.
                        final Optional<AggregateRecord> aggregateRecord = getTargetAggregate(
                                item,
                                maxItemsPerAggregate,
                                maxUncompressedByteSize);

                        if (aggregateRecord.isPresent()) {
                            currentRecord = aggregateRecord.get();

                        } else {
                            // Create new record.
                            final long id = aggregateId.incrementAndGet();
                            final AggregateRecord record = new AggregateRecord(
                                    id,
                                    System.currentTimeMillis(),
                                    item.getFeedName(),
                                    item.getTypeName(),
                                    0L,
                                    0,
                                    false,
                                    null);
                            operationWriteQueue.add(context -> context
                                    .executeInsert(record));
                            currentRecord = record;
                        }
                    }

                    currentRecord.setItems(currentRecord.getItems() + 1);
                    currentRecord.setByteSize(currentRecord.getByteSize() + item.getTotalByteSize());
                    // Mark the item as added by setting the aggregate id.
                    final long aggregateId = currentRecord.getId();
                    operationWriteQueue.add(context -> context
                            .update(SOURCE_ITEM)
                            .set(SOURCE_ITEM.FK_AGGREGATE_ID, aggregateId)
                            .setNull(SOURCE_ITEM.NEW_POSITION)
                            .where(SOURCE_ITEM.ID.eq(item.getId()))
                            .execute());
                }
            }

            if (currentRecord != null) {
                // Commit and nullify current record.
                final AggregateRecord record = currentRecord;
                operationWriteQueue.add(context -> context
                        .executeUpdate(record));
                jooq.transaction(operationWriteQueue::flush);
                operationWriteQueue.clear();
            }
        }
    }

    private Optional<AggregateRecord> getTargetAggregate(final RepoSourceItemRef item,
                                                         final int maxItemsPerAggregate,
                                                         final long maxUncompressedByteSize) {
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - item.getTotalByteSize());

        final Condition condition = DSL
                .and(item.getFeedName() == null
                        ? AGGREGATE.FEED_NAME.isNull()
                        : AGGREGATE.FEED_NAME.equal(item.getFeedName()))
                .and(item.getTypeName() == null
                        ? AGGREGATE.TYPE_NAME.isNull()
                        : AGGREGATE.TYPE_NAME.equal(item.getTypeName()))
                .and(AGGREGATE.BYTE_SIZE.lessOrEqual(maxAggregateSize))
                .and(AGGREGATE.ITEMS.lessThan(maxItemsPerAggregate))
                .and(AGGREGATE.COMPLETE.isFalse());

        // Try to find an appropriate record.
        return jooq.readOnlyTransactionResult(context -> context
                .selectFrom(AGGREGATE)
                .where(condition)
                .orderBy(AGGREGATE.CREATE_TIME_MS)
                .limit(1)
                .fetchOptional());
    }

    /**
     * Fetch a list of all source entries that belong to the specified aggregate.
     *
     * @param aggregateId The id of the aggregate to get source entries for.
     * @return A list of source entries for the aggregate.
     */
    public Map<RepoSource, List<RepoSourceItem>> fetchSourceItems(final long aggregateId) {
        final Map<Long, RepoSourceItem> items = new HashMap<>();
        final Map<RepoSource, List<RepoSourceItem>> resultMap =
                new TreeMap<>(Comparator.comparing(RepoSource::getId));

        // Get all of the source zip entries that we want to write to the forwarding location.
        jooq.readOnlyTransactionResult(context -> context
                        .select(
                                SOURCE_ENTRY.ID,
                                SOURCE_ENTRY.EXTENSION,
                                SOURCE_ENTRY.EXTENSION_TYPE,
                                SOURCE_ENTRY.BYTE_SIZE,
                                SOURCE_ITEM.ID,
                                SOURCE_ITEM.FK_SOURCE_ID,
                                SOURCE_ITEM.NAME,
                                SOURCE_ITEM.FEED_NAME,
                                SOURCE_ITEM.TYPE_NAME,
                                SOURCE_ITEM.FK_AGGREGATE_ID,
                                SOURCE.ID,
                                SOURCE.FILE_STORE_ID,
                                SOURCE.FEED_NAME,
                                SOURCE.TYPE_NAME,
                                SOURCE.EXAMINED)
                        .from(SOURCE_ENTRY)
                        .join(SOURCE_ITEM).on(SOURCE_ITEM.ID.eq(SOURCE_ENTRY.FK_SOURCE_ITEM_ID))
                        .join(SOURCE).on(SOURCE.ID.eq(SOURCE_ITEM.FK_SOURCE_ID))
                        .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId))
                        .orderBy(SOURCE.ID, SOURCE_ITEM.ID, SOURCE_ENTRY.EXTENSION_TYPE, SOURCE_ENTRY.EXTENSION)
                        .fetch())
                .forEach(r -> {
                    final long id = r.get(SOURCE_ITEM.ID);

                    final RepoSourceItem sourceItem = items.computeIfAbsent(id, k -> {
                        final RepoSource source = RepoSource.builder()
                                .id(r.get(SOURCE.ID))
                                .fileStoreId(r.get(SOURCE.FILE_STORE_ID))
                                .feedName(r.get(SOURCE.FEED_NAME))
                                .typeName(r.get(SOURCE.TYPE_NAME))
                                .build();

                        final RepoSourceItem item = RepoSourceItem.builder()
                                .source(source)
                                .name(r.get(SOURCE_ITEM.NAME))
                                .feedName(r.get(SOURCE_ITEM.FEED_NAME))
                                .typeName(r.get(SOURCE_ITEM.TYPE_NAME))
                                .aggregateId(r.get(SOURCE_ITEM.FK_AGGREGATE_ID))
                                .build();

                        resultMap.computeIfAbsent(source, s -> new ArrayList<>()).add(item);

                        return item;
                    });

                    final RepoSourceEntry entry = RepoSourceEntry.builder()
                            .id(r.get(SOURCE_ENTRY.ID))
                            .type(StroomZipFileType.TYPE_MAP.get(r.get(SOURCE_ENTRY.EXTENSION_TYPE)))
                            .extension(r.get(SOURCE_ENTRY.EXTENSION))
                            .byteSize(r.get(SOURCE_ENTRY.BYTE_SIZE))
                            .build();
                    sourceItem.addEntry(entry);
                });

        return resultMap;
    }

    public int countAggregates() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, AGGREGATE));
    }
}
