package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.proxy.repo.queue.WriteQueue;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
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
                 final ProxyDbConfig dbConfig) {
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
                                AGGREGATE.FK_FEED_ID,
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
                            r.get(AGGREGATE.FK_FEED_ID));
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
                        .select(AGGREGATE.ID,
                                AGGREGATE.FK_FEED_ID)
                        .from(AGGREGATE)
                        .where(condition)
                        .orderBy(AGGREGATE.CREATE_TIME_MS)
                        .limit(limit)
                        .fetch())
                .map(r -> new Aggregate(
                        r.get(AGGREGATE.ID),
                        r.get(AGGREGATE.FK_FEED_ID)));

        recordQueue.add(() -> {
            for (final Aggregate aggregate : list) {
                aggregateWriteQueue.add(context -> context
                        .update(AGGREGATE)
                        .set(AGGREGATE.COMPLETE, true)
                        .set(AGGREGATE.NEW_POSITION, aggregateNewPosition.incrementAndGet())
                        .where(AGGREGATE.ID.eq(aggregate.id()))
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
            final Map<Long, List<RepoSourceItemRef>> itemMap = newSourceItems
                    .list()
                    .stream()
                    .collect(Collectors.groupingBy(RepoSourceItemRef::feedId));

            final OperationWriteQueue operationWriteQueue = new OperationWriteQueue();

            AggregateRecord currentRecord = null;

            for (final List<RepoSourceItemRef> items : itemMap.values()) {
                for (final RepoSourceItemRef item : items) {

                    // Get an aggregate to fit the item.
                    if (currentRecord != null) {
                        if (!Objects.equals(currentRecord.getFkFeedId(), item.feedId()) ||
                                currentRecord.getItems() >= maxItemsPerAggregate ||
                                (currentRecord.getItems() > 0 &&
                                        currentRecord.getByteSize() + item.totalByteSize() >
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
                                    item.feedId(),
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
                    currentRecord.setByteSize(currentRecord.getByteSize() + item.totalByteSize());
                    // Mark the item as added by setting the aggregate id.
                    final long aggregateId = currentRecord.getId();
                    operationWriteQueue.add(context -> context
                            .update(SOURCE_ITEM)
                            .set(SOURCE_ITEM.FK_AGGREGATE_ID, aggregateId)
                            .setNull(SOURCE_ITEM.NEW_POSITION)
                            .where(SOURCE_ITEM.ID.eq(item.id()))
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
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - item.totalByteSize());

        final Condition condition = DSL
                .and(AGGREGATE.FK_FEED_ID.eq(item.feedId()))
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

    public int countAggregates() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, AGGREGATE));
    }
}
