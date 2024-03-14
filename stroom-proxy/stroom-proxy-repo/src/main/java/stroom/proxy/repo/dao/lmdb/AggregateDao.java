package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.dao.lmdb.serde.AggregateKeySerde;
import stroom.proxy.repo.dao.lmdb.serde.AggregateValueSerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class AggregateDao implements Clearable, Flushable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateDao.class);

    private final LmdbEnv env;
    private final Db<AggregateKey, AggregateValue> db;
    private final ByteBufferPool byteBufferPool;


    private final SourceItemDao sourceItemDao;
//    private final LongSerde prefixSerde;
//    private final AggregateKeySerde aggregateKeySerde;
//    private final AggregateValueSerde aggregateValueSerde;
//    private final RepoSourceValueSerde valueSerde;

    private final LmdbQueue<AggregateKey> newAggregateQueue;
    //    private final LmdbQueue<Long> examinedSourceQueue;
    private final LmdbQueue<Long> deletableAggregateQueue;

    private final AtomicLong idSuffix = new AtomicLong();

    @Inject
    public AggregateDao(final LmdbEnv env,
                        final ByteBufferPool byteBufferPool,
                        final SourceItemDao sourceItemDao) {
        try {
            this.env = env;
            this.byteBufferPool = byteBufferPool;
            this.sourceItemDao = sourceItemDao;
            this.db = env.openDb("aggregate", new AggregateKeySerde(), new AggregateValueSerde());

            newAggregateQueue = new LmdbQueue<>(env, "new-aggregate", new AggregateKeySerde());
//            examinedSourceQueue = new LmdbQueue<>(env, "examined-source", keySerde, keySerde);
            deletableAggregateQueue = new LmdbQueue<>(env, "deletable-aggregate", new LongSerde());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

//        this.feedDao = feedDao;
    }

//    private final AtomicLong aggregateId = new AtomicLong();
//
//    private final AtomicLong aggregateNewPosition = new AtomicLong();
//
//    private final RecordQueue recordQueue;
//    private final OperationWriteQueue aggregateWriteQueue;
//    private final ReadQueue<Aggregate> aggregateReadQueue;
//    private final QueueMonitor queueMonitor;
//
//    private final LmdbQueue<Long> newAggregateQueue;

//    @Inject
//    AggregateDao(final ProxyDbConfig dbConfig,
//                 final QueueMonitors queueMonitors) {
//        queueMonitor = queueMonitors.create(4, "Aggregates");
//
//        init();
//
//        aggregateWriteQueue = new OperationWriteQueue();
//        final List<WriteQueue> writeQueues = List.of(aggregateWriteQueue);
//
//        aggregateReadQueue = new ReadQueue<>(this::read, dbConfig.getBatchSize());
//        final List<ReadQueue<?>> readQueues = List.of(aggregateReadQueue);
//
//        recordQueue = new RecordQueue(jooq, writeQueues, readQueues, dbConfig.getBatchSize());
//    }
//
//    private long read(final long currentReadPos, final long limit, List<Aggregate> readQueue) {
//        queueMonitor.setReadPos(currentReadPos);
//
//        final AtomicLong pos = new AtomicLong(currentReadPos);
//        jooq.readOnlyTransactionResult(context -> context
//                        .select(AGGREGATE.ID,
//                                AGGREGATE.FK_FEED_ID,
//                                AGGREGATE.NEW_POSITION)
//                        .from(AGGREGATE)
//                        .where(AGGREGATE.NEW_POSITION.isNotNull())
//                        .and(AGGREGATE.NEW_POSITION.gt(currentReadPos))
//                        .orderBy(AGGREGATE.NEW_POSITION)
//                        .limit(limit)
//                        .fetch())
//                .forEach(r -> {
//                    final long newPosition = r.get(AGGREGATE.NEW_POSITION);
//                    pos.set(newPosition);
//                    queueMonitor.setBufferPos(newPosition);
//
//                    final Aggregate aggregate = new Aggregate(
//                            r.get(AGGREGATE.ID),
//                            r.get(AGGREGATE.FK_FEED_ID));
//                    readQueue.add(aggregate);
//                });
//        return pos.get();
//    }
//
//    private void init() {
//        jooq.readOnlyTransaction(context -> {
//            aggregateId.set(JooqUtil
//                    .getMaxId(context, AGGREGATE, AGGREGATE.ID)
//                    .orElse(0L));
//
//            final long newPosition = JooqUtil
//                    .getMaxId(context, AGGREGATE, AGGREGATE.NEW_POSITION)
//                    .orElse(0L);
//            queueMonitor.setWritePos(newPosition);
//            aggregateNewPosition.set(newPosition);
//        });
//    }
//
//    public void clear() {
//        jooq.transaction(context -> {
//            JooqUtil.deleteAll(context, AGGREGATE);
//            JooqUtil.checkEmpty(context, AGGREGATE);
//        });
//        recordQueue.clear();
//        init();
//    }
//
////    /**
////     * Close all aggregates that meet the supplied criteria.
////     */
////    public Batch<Aggregate> getClosableAggregates(final int maxItemsPerAggregate,
////                                                  final long maxUncompressedByteSize,
////                                                  final long oldestMs,
////                                                  final long limit) {
////        final Condition condition =
////                AGGREGATE.COMPLETE.eq(false)
////                        .and(
////                                DSL.or(
////                                        AGGREGATE.ITEMS.greaterOrEqual(maxItemsPerAggregate),
////                                        AGGREGATE.BYTE_SIZE.greaterOrEqual(maxUncompressedByteSize),
////                                        AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldestMs)
////                                )
////                        );
////        final List<Aggregate> list = jooq.readOnlyTransactionResult(context -> context
////                        .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
////                        .from(AGGREGATE)
////                        .where(condition)
////                        .orderBy(AGGREGATE.CREATE_TIME_MS)
////                        .limit(limit)
////                        .fetch())
////                .map(r -> new Aggregate(
////                        r.value1(),
////                        r.value2(),
////                        r.value3()));
////        return new Batch<>(list, list.size() == limit);
////    }
//
//

    /**
     * Close all aggregates that meet the supplied criteria.
     */
    public synchronized void closeAggregates(final int maxItemsPerAggregate,
                                             final long maxUncompressedByteSize,
                                             final long maxAggregateAgeMs) {
        final long oldestMs = System.currentTimeMillis() - maxAggregateAgeMs;
        db.getAll((k, v) -> {

            if (!v.complete() &&
                    v.items() >= maxItemsPerAggregate &&
                    v.byteSize() >= maxUncompressedByteSize &&
                    k.createTimeMs() <= oldestMs) {
                final AggregateValue updatedAggregateValue = new AggregateValue(
                        v.byteSize(),
                        v.items(),
                        true);
                db.put(k, updatedAggregateValue);

//                final PooledByteBuffer updatedByteBuffer = aggregateValueSerde.serialize(updatedAggregateValue);
//                dbi.put(txn, keyVal.key(), updatedByteBuffer.getByteBuffer());
//                updatedByteBuffer.release();

                newAggregateQueue.put(k);
            }
        });


//        env.writeAsync(txn -> {
//            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
//                    final AggregateKey aggregateKey = aggregateKeySerde.deserialize(keyVal.key());
//                    final AggregateValue aggregateValue = aggregateValueSerde.deserialize(keyVal.val());
//                    if (!aggregateValue.complete() &&
//                            aggregateValue.items() >= maxItemsPerAggregate &&
//                            aggregateValue.byteSize() >= maxUncompressedByteSize &&
//                            aggregateKey.createTimeMs() <= oldestMs) {
//                        final AggregateValue updatedAggregateValue = new AggregateValue(
//                                aggregateValue.byteSize(),
//                                aggregateValue.items(),
//                                true);
//                        final PooledByteBuffer updatedByteBuffer = aggregateValueSerde.serialize(updatedAggregateValue);
//                        dbi.put(txn, keyVal.key(), updatedByteBuffer.getByteBuffer());
//                        updatedByteBuffer.release();
//
//                        newAggregateQueue.put(aggregateKey);
//                    }
//                }
//            }
//        });

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
//                        .select(AGGREGATE.ID,
//                                AGGREGATE.FK_FEED_ID)
//                        .from(AGGREGATE)
//                        .where(condition)
//                        .orderBy(AGGREGATE.CREATE_TIME_MS)
//                        .limit(limit)
//                        .fetch())
//                .map(r -> new Aggregate(
//                        r.get(AGGREGATE.ID),
//                        r.get(AGGREGATE.FK_FEED_ID)));
//
//        recordQueue.add(() -> {
//            for (final Aggregate aggregate : list) {
//                final long newPosition = aggregateNewPosition.incrementAndGet();
//                queueMonitor.setWritePos(newPosition);
//
//                aggregateWriteQueue.add(context -> context
//                        .update(AGGREGATE)
//                        .set(AGGREGATE.COMPLETE, true)
//                        .set(AGGREGATE.NEW_POSITION, newPosition)
//                        .where(AGGREGATE.ID.eq(aggregate.id()))
//                        .execute());
//            }
//        });
//
//        // Ensure all DB changes are flushed to the db.
//        recordQueue.flush();
//
//        return list.size();
    }

    //
    public AggregateKey getNewAggregate() {
        return newAggregateQueue.take();
    }

    //    public Optional<AggregateKey> getNewAggregate(final long time, final TimeUnit timeUnit) {
//        return newAggregateQueue.take(time, timeUnit);
//    }
//
//    public Batch<Aggregate> getNewAggregates(final long timeout,
//                                             final TimeUnit timeUnit) {
//        return recordQueue.getBatch(aggregateReadQueue, timeout, timeUnit);
//    }
//
    public synchronized void addItem(final RepoSourceItemRef item,
                                     final int maxItemsPerAggregate,
                                     final long maxUncompressedByteSize) {

        // Get an aggregate to fit the item.
        final Optional<KV<AggregateKey, AggregateValue>> optional = getTargetAggregate(
                item,
                maxItemsPerAggregate,
                maxUncompressedByteSize);

        KV<AggregateKey, AggregateValue> kv;
        if (optional.isPresent()) {
            // Update aggregate.
            kv = optional.get();
            AggregateValue value = kv.value();
            value = new AggregateValue(
                    value.byteSize() + item.totalByteSize(),
                    value.items() + 1,
                    value.complete());
            kv = new KV<>(kv.key(), value);

        } else {
            // Create new aggregate.
            final AggregateKey key = new AggregateKey(
                    item.feedId(),
                    System.currentTimeMillis(),
                    idSuffix.incrementAndGet());
            final AggregateValue value = new AggregateValue(item.totalByteSize(), 1, false);
            kv = new KV<>(key, value);
        }

        db.put(kv.key(), kv.value());
//        final PooledByteBuffer keyByteBuffer = aggregateKeySerde.serialize(kv.key());
//        final PooledByteBuffer valueByteBuffer = aggregateValueSerde.serialize(kv.value());
//        env.writeAsync(txn -> {
//            dbi.put(txn, keyByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
//            keyByteBuffer.release();
//            valueByteBuffer.release();
//        });


//        currentRecord.setItems(currentRecord.getItems() + 1);
//        currentRecord.setByteSize(currentRecord.getByteSize() + item.totalByteSize());


//        // Mark the item as added by setting the aggregate id.
//        sourceItemDao.setAggregate(item.id(), );
//        final long aggregateId = currentRecord.getId();
//        operationWriteQueue.add(context -> context
//                .update(SOURCE_ITEM)
//                .set(SOURCE_ITEM.FK_AGGREGATE_ID, aggregateId)
//                .setNull(SOURCE_ITEM.NEW_POSITION)
//                .where(SOURCE_ITEM.ID.eq(item.id()))
//                .execute());
//        }
//    }
//
//            if (currentRecord != null) {
//                // Commit and nullify current record.
//                final AggregateRecord record = currentRecord;
//                operationWriteQueue.add(context -> context
//                        .executeUpdate(record));
//                jooq.transaction(operationWriteQueue::flush);
//                operationWriteQueue.clear();
//            }
//        }
    }

    private Optional<KV<AggregateKey, AggregateValue>> getTargetAggregate(final RepoSourceItemRef item,
                                                                          final int maxItemsPerAggregate,
                                                                          final long maxUncompressedByteSize) {
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - item.totalByteSize());

        // Try to find an appropriate aggregate.
        final LongSerde prefixSerde = new LongSerde();
        try (
                final PooledByteBuffer min = prefixSerde.serialize(item.feedId(), env.getByteBufferPool());
                final PooledByteBuffer max = prefixSerde.serialize(item.feedId() + 1, env.getByteBufferPool())) {
            final KeyRange<ByteBuffer> keyRange = KeyRange.closedOpen(min.getByteBuffer(), max.getByteBuffer());
            return Optional.ofNullable(db.scan(keyRange, (k, v) -> {
                if (k.feedId() == item.feedId() &&
                        v.byteSize() <= maxAggregateSize &&
                        v.items() < maxItemsPerAggregate &&
                        !v.complete()) {
                    return new KV<>(k, v);
                }
                return null;
            }));
        }


//        // Try to find an appropriate aggregate.
//        return env.readResult(txn -> {
//            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, keyRange)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
//                    final AggregateKey aggregateKey = aggregateKeySerde.deserialize(keyVal.key());
//                    final AggregateValue aggregateValue = aggregateValueSerde.deserialize(keyVal.val());
//                    if (aggregateKey.feedId() == item.feedId() &&
//                            aggregateValue.byteSize() <= maxAggregateSize &&
//                            aggregateValue.items() < maxItemsPerAggregate &&
//                            !aggregateValue.complete()) {
//                        return Optional.of(new KV<>(aggregateKey, aggregateValue));
//                    }
//                }
//                return Optional.empty();
//
//            } finally {
//                min.release();
//                max.release();
//            }
//        });
    }
//
//    public int countAggregates() {
//        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, AGGREGATE));
//    }


    @Override
    public void clear() {
        db.clear();
        newAggregateQueue.clear();
//        examinedSourceQueue.clear();
        deletableAggregateQueue.clear();
    }

    @Override
    public void flush() {
        newAggregateQueue.flush();
//        examinedSourceQueue.flush();
        deletableAggregateQueue.flush();
    }

    LmdbQueue<AggregateKey> getNewAggregateQueue() {
        return newAggregateQueue;
    }

    LmdbQueue<Long> getDeletableAggregateQueue() {
        return deletableAggregateQueue;
    }

//    LmdbQueue<Long> getExaminedSourceQueue() {
//        return examinedSourceQueue;
//    }

    public long countAggregates() {
        return db.count();
    }
}
