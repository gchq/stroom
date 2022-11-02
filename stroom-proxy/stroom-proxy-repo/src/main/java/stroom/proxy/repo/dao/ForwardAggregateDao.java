package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.ForwardAggregate;
import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.db.jooq.tables.records.ForwardAggregateRecord;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BindWriteQueue;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.util.logging.Metrics;
import stroom.util.shared.Flushable;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.TableField;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardDest.FORWARD_DEST;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class ForwardAggregateDao implements Flushable {

    private static final Field<?>[] FORWARD_AGGREGATE_COLUMNS = new Field<?>[]{
            FORWARD_AGGREGATE.ID,
            FORWARD_AGGREGATE.UPDATE_TIME_MS,
            FORWARD_AGGREGATE.FK_FORWARD_DEST_ID,
            FORWARD_AGGREGATE.FK_AGGREGATE_ID,
            FORWARD_AGGREGATE.SUCCESS,
            FORWARD_AGGREGATE.NEW_POSITION};

    private static final Condition NEW_AGGREGATE_CONDITION =
            AGGREGATE.NEW_POSITION.isNull().andExists(DSL
                    .select(FORWARD_AGGREGATE.ID)
                    .from(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(AGGREGATE.ID)));

    private static final Condition DELETE_AGGREGATE_CONDITION =
            AGGREGATE.NEW_POSITION.isNull().andNotExists(DSL
                    .select(FORWARD_AGGREGATE.ID)
                    .from(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(AGGREGATE.ID)));

    private final SqliteJooqHelper jooq;
    private final ProxyDbConfig dbConfig;
    private final AtomicLong forwardAggregateId = new AtomicLong();


    private final AtomicLong forwardAggregateNewPosition = new AtomicLong();
    private final AtomicLong forwardAggregateRetryPosition = new AtomicLong();

    private final RecordQueue recordQueue;
    private final OperationWriteQueue aggregateUpdateQueue;
    private final BindWriteQueue forwardAggregateWriteQueue;
    private final ReadQueue<ForwardAggregate> forwardAggregateReadQueue;


    private final RecordQueue retryRecordQueue;
    private final OperationWriteQueue retryUpdateQueue;
    private final ReadQueue<ForwardAggregate> retryReadQueue;

    @Inject
    ForwardAggregateDao(final SqliteJooqHelper jooq,
                        final ProxyDbConfig dbConfig) {
        this.jooq = jooq;
        this.dbConfig = dbConfig;
        init();

        aggregateUpdateQueue = new OperationWriteQueue();
        forwardAggregateWriteQueue = new BindWriteQueue(FORWARD_AGGREGATE, FORWARD_AGGREGATE_COLUMNS);
        forwardAggregateReadQueue = new ReadQueue<>(this::readNew, dbConfig.getBatchSize());
        recordQueue = new RecordQueue(
                jooq,
                List.of(forwardAggregateWriteQueue, aggregateUpdateQueue),
                List.of(forwardAggregateReadQueue),
                dbConfig.getBatchSize());

        retryUpdateQueue = new OperationWriteQueue();
        retryReadQueue = new ReadQueue<>(this::readRetry, dbConfig.getBatchSize());
        retryRecordQueue = new RecordQueue(
                jooq,
                Collections.singletonList(retryUpdateQueue),
                Collections.singletonList(retryReadQueue),
                dbConfig.getBatchSize());
    }

    private long readNew(final long currentReadPos, final long limit, List<ForwardAggregate> readQueue) {
        return read(currentReadPos, limit, readQueue, FORWARD_AGGREGATE.NEW_POSITION);
    }

    private long readRetry(final long currentReadPos, final long limit, List<ForwardAggregate> readQueue) {
        return read(currentReadPos, limit, readQueue, FORWARD_AGGREGATE.RETRY_POSITION);
    }

    private long read(final long currentReadPos,
                      final long limit,
                      final List<ForwardAggregate> readQueue,
                      final TableField<ForwardAggregateRecord, Long> positionField) {
        final AtomicLong pos = new AtomicLong(currentReadPos);
        jooq.readOnlyTransactionResult(context -> context
                        .select(FORWARD_AGGREGATE.ID,
                                FORWARD_AGGREGATE.UPDATE_TIME_MS,
                                FORWARD_AGGREGATE.FK_FORWARD_DEST_ID,
                                FORWARD_DEST.NAME,
                                AGGREGATE.FK_FEED_ID,
                                FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                                FORWARD_AGGREGATE.SUCCESS,
                                FORWARD_AGGREGATE.ERROR,
                                FORWARD_AGGREGATE.TRIES,
                                FORWARD_AGGREGATE.LAST_TRY_TIME_MS,
                                positionField)
                        .from(FORWARD_AGGREGATE)
                        .join(FORWARD_DEST).on(FORWARD_DEST.ID.eq(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID))
                        .join(AGGREGATE).on(AGGREGATE.ID.eq(FORWARD_AGGREGATE.FK_AGGREGATE_ID))
                        .where(positionField.isNotNull())
                        .and(positionField.gt(currentReadPos))
                        .orderBy(positionField)
                        .limit(limit)
                        .fetch())
                .forEach(r -> {
                    pos.set(r.get(positionField));
                    final ForwardDest forwardDest = new ForwardDest(r.get(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID),
                            r.get(FORWARD_DEST.NAME));
                    final Aggregate aggregate = new Aggregate(
                            r.get(FORWARD_AGGREGATE.FK_AGGREGATE_ID),
                            r.get(AGGREGATE.FK_FEED_ID));
                    final ForwardAggregate forwardAggregate = new ForwardAggregate(
                            r.get(FORWARD_AGGREGATE.ID),
                            r.get(FORWARD_AGGREGATE.UPDATE_TIME_MS),
                            aggregate,
                            forwardDest,
                            r.get(FORWARD_AGGREGATE.SUCCESS),
                            r.get(FORWARD_AGGREGATE.ERROR),
                            r.get(FORWARD_AGGREGATE.TRIES),
                            r.get(FORWARD_AGGREGATE.LAST_TRY_TIME_MS));
                    readQueue.add(forwardAggregate);
                });
        return pos.get();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            forwardAggregateId.set(JooqUtil
                    .getMaxId(context, FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID)
                    .orElse(0L));

            forwardAggregateNewPosition.set(JooqUtil
                    .getMaxId(context, FORWARD_AGGREGATE, FORWARD_AGGREGATE.NEW_POSITION)
                    .orElse(0L));

            forwardAggregateRetryPosition.set(JooqUtil
                    .getMaxId(context, FORWARD_AGGREGATE, FORWARD_AGGREGATE.RETRY_POSITION)
                    .orElse(0L));
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, FORWARD_AGGREGATE);
            JooqUtil.checkEmpty(context, FORWARD_AGGREGATE);
        });
        recordQueue.clear();
        retryRecordQueue.clear();
        init();
    }

    /**
     * Add forward aggregates for any new dests that have been added since the application last ran.
     *
     * @param newForwardDests New dests to add forward aggregate entries for.
     */
    public void addNewForwardAggregates(final List<ForwardDest> newForwardDests) {
        if (newForwardDests.size() > 0) {
            final AtomicLong minId = new AtomicLong();
            final int batchSize = dbConfig.getBatchSize();
            boolean full = true;
            while (full) {
                final List<Aggregate> aggregates = new ArrayList<>();
                jooq.readOnlyTransactionResult(context -> context
                                .select(AGGREGATE.ID,
                                        AGGREGATE.FK_FEED_ID)
                                .from(AGGREGATE)
                                .where(NEW_AGGREGATE_CONDITION)
                                .and(AGGREGATE.ID.gt(minId.get()))
                                .orderBy(AGGREGATE.ID)
                                .limit(batchSize)
                                .fetch())
                        .forEach(r -> {
                            minId.set(r.get(AGGREGATE.ID));
                            final Aggregate aggregate = new Aggregate(
                                    r.get(AGGREGATE.ID),
                                    r.get(AGGREGATE.FK_FEED_ID));
                            aggregates.add(aggregate);
                        });

                final Batch<Aggregate> batch = new Batch<>(aggregates, aggregates.size() == batchSize);
                createForwardAggregates(batch, newForwardDests);
                full = batch.full();
            }
        }
    }

    public void removeOldForwardAggregates(final List<ForwardDest> oldForwardDests) {
        if (oldForwardDests.size() > 0) {
            final List<Integer> oldIdList = oldForwardDests
                    .stream()
                    .map(ForwardDest::getId)
                    .collect(Collectors.toList());

            jooq.transaction(context -> context
                    .deleteFrom(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID.in(oldIdList))
                    .execute());

            final AtomicLong minId = new AtomicLong();
            final int batchSize = dbConfig.getBatchSize();
            boolean full = true;
            while (full) {
                final List<Aggregate> aggregates = new ArrayList<>();
                jooq.readOnlyTransactionResult(context -> context
                                .select(AGGREGATE.ID, AGGREGATE.FK_FEED_ID)
                                .from(AGGREGATE)
                                .where(DELETE_AGGREGATE_CONDITION)
                                .and(AGGREGATE.ID.gt(minId.get()))
                                .orderBy(AGGREGATE.ID)
                                .limit(batchSize)
                                .fetch())
                        .forEach(r -> {
                            minId.set(r.get(AGGREGATE.ID));
                            final Aggregate aggregate = new Aggregate(
                                    r.get(AGGREGATE.ID),
                                    r.get(AGGREGATE.FK_FEED_ID));
                            aggregates.add(aggregate);
                        });

                final Batch<Aggregate> batch = new Batch<>(aggregates, aggregates.size() == batchSize);
                deleteAggregates(batch);
                full = batch.full();
            }
        }
    }

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    public void createForwardAggregates(final Batch<Aggregate> aggregates,
                                        final List<ForwardDest> forwardDests) {
        recordQueue.add(() -> {
            for (final Aggregate aggregate : aggregates.list()) {
                for (final ForwardDest forwardDest : forwardDests) {
                    final Object[] row = new Object[FORWARD_AGGREGATE_COLUMNS.length];
                    row[0] = forwardAggregateId.incrementAndGet();
                    row[1] = System.currentTimeMillis();
                    row[2] = forwardDest.getId();
                    row[3] = aggregate.id();
                    row[4] = false;
                    row[5] = forwardAggregateNewPosition.incrementAndGet();
                    forwardAggregateWriteQueue.add(row);
                }

                // Remove the queue position from the aggregate so we don't try and create forwarders again.
                aggregateUpdateQueue.add(context -> context
                        .update(AGGREGATE)
                        .setNull(AGGREGATE.NEW_POSITION)
                        .where(AGGREGATE.ID.eq(aggregate.id()))
                        .execute());
            }
        });
    }

    public Batch<ForwardAggregate> getNewForwardAggregates() {
        return recordQueue.getBatch(forwardAggregateReadQueue);
    }

    public Batch<ForwardAggregate> getRetryForwardAggregate() {
        return retryRecordQueue.getBatch(retryReadQueue);
    }

//    private Optional<ForwardAggregate> getForwardAggregate(final WorkQueue workQueue,
//                                                           final Field<Long> positionField) {
//        return workQueue.get(position ->
//                getForwardAggregateAtQueuePosition(position, positionField));
//    }

    public Batch<ForwardAggregate> getNewForwardAggregates(final long timeout,
                                                           final TimeUnit timeUnit) {
        return recordQueue.getBatch(forwardAggregateReadQueue, timeout, timeUnit);
    }

    public Batch<ForwardAggregate> getRetryForwardAggregate(final long timeout,
                                                            final TimeUnit timeUnit) {
        return retryRecordQueue.getBatch(retryReadQueue, timeout, timeUnit);
    }

//    private Optional<ForwardAggregate> getForwardAggregate(final WorkQueue workQueue,
//                                                           final Field<Long> positionField,
//                                                           final long timeout,
//                                                           final TimeUnit timeUnit) {
//        return workQueue.get(position ->
//                getForwardAggregateAtQueuePosition(position, positionField), timeout, timeUnit);
//    }
//
//    private Optional<ForwardAggregate> getForwardAggregateAtQueuePosition(final long position,
//                                                                          final Field<Long> positionField) {
//        return jooq.readOnlyTransactionResult(context -> context
//                        .select(FORWARD_AGGREGATE.ID,
//                                FORWARD_AGGREGATE.UPDATE_TIME_MS,
//                                FORWARD_AGGREGATE.FK_FORWARD_DEST_ID,
//                                FORWARD_DEST.NAME,
//                                AGGREGATE.FEED_NAME,
//                                AGGREGATE.TYPE_NAME,
//                                FORWARD_AGGREGATE.FK_AGGREGATE_ID,
//                                FORWARD_AGGREGATE.SUCCESS,
//                                FORWARD_AGGREGATE.ERROR,
//                                FORWARD_AGGREGATE.TRIES)
//                        .from(FORWARD_AGGREGATE)
//                        .join(FORWARD_DEST).on(FORWARD_DEST.ID.eq(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID))
//                        .join(AGGREGATE).on(AGGREGATE.ID.eq(FORWARD_AGGREGATE.FK_AGGREGATE_ID))
//                        .where(positionField.eq(position))
//                        .orderBy(FORWARD_AGGREGATE.ID)
//                        .fetchOptional())
//                .map(r -> {
//                    final ForwardUrl forwardUrl = new ForwardUrl(r.get(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID),
//                            r.get(FORWARD_DEST.NAME));
//                    final Aggregate aggregate = new Aggregate(
//                            r.get(FORWARD_AGGREGATE.FK_AGGREGATE_ID),
//                            r.get(AGGREGATE.FEED_NAME),
//                            r.get(AGGREGATE.TYPE_NAME));
//                    return new ForwardAggregate(
//                            r.get(FORWARD_AGGREGATE.ID),
//                            r.get(FORWARD_AGGREGATE.UPDATE_TIME_MS),
//                            aggregate,
//                            forwardUrl,
//                            r.get(FORWARD_AGGREGATE.SUCCESS),
//                            r.get(FORWARD_AGGREGATE.ERROR),
//                            r.get(FORWARD_AGGREGATE.TRIES));
//                });
//    }

    public void update(final ForwardAggregate forwardAggregate) {
        if (forwardAggregate.isSuccess()) {
            final long aggregateId = forwardAggregate.getAggregate().id();

            // Mark success and see if we can delete this record and cascade.
            jooq.transaction(context -> {
                // We finished forwarding an aggregate so delete all related forward aggregate records.
                updateForwardAggregate(context, forwardAggregate, null);

                final Condition condition = FORWARD_AGGREGATE.FK_AGGREGATE_ID
                        .eq(forwardAggregate.getAggregate().id())
                        .and(FORWARD_AGGREGATE.SUCCESS.ne(true));
                final int remainingForwards = context.fetchCount(FORWARD_AGGREGATE, condition);
                if (remainingForwards == 0) {
                    deleteAggregate(context, aggregateId);
                }
            });
        } else {
            // Update and schedule for retry.
            retryRecordQueue.add(() ->
                    retryUpdateQueue.add(context ->
                            updateForwardAggregate(
                                    context,
                                    forwardAggregate,
                                    forwardAggregateRetryPosition.incrementAndGet())));
        }
    }

    private void updateForwardAggregate(final DSLContext context,
                                        final ForwardAggregate forwardAggregate,
                                        final Long retryPosition) {
        context
                .update(FORWARD_AGGREGATE)
                .set(FORWARD_AGGREGATE.UPDATE_TIME_MS, forwardAggregate.getUpdateTimeMs())
                .set(FORWARD_AGGREGATE.SUCCESS, forwardAggregate.isSuccess())
                .set(FORWARD_AGGREGATE.ERROR, forwardAggregate.getError())
                .setNull(FORWARD_AGGREGATE.NEW_POSITION)
                .set(FORWARD_AGGREGATE.TRIES, forwardAggregate.getTries())
                .set(FORWARD_AGGREGATE.LAST_TRY_TIME_MS, forwardAggregate.getLastTryTimeMs())
                .set(FORWARD_AGGREGATE.RETRY_POSITION, retryPosition)
                .where(FORWARD_AGGREGATE.ID.eq(forwardAggregate.getId()))
                .execute();
    }

    private void deleteAggregates(final Batch<Aggregate> aggregates) {
        jooq.transaction(context -> {
            for (final Aggregate aggregate : aggregates.list()) {
                deleteAggregate(context, aggregate.id());
            }
        });
    }

    private void deleteAggregate(final DSLContext context, final long aggregateId) {
        // Get source items and sources.
        final Result<Record2<Long, Integer>> result = context
                .select(SOURCE.ID, DSL.count(SOURCE_ITEM.ID))
                .from(SOURCE)
                .join(SOURCE_ITEM)
                .on(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))
                .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId))
                .groupBy(SOURCE.ID)
                .fetch();

        // Update the source item count.
        for (final Record2<Long, Integer> record : result) {
            Metrics.measure("Update source item count", () -> {
                context
                        .update(SOURCE)
                        .set(SOURCE.ITEM_COUNT, SOURCE.ITEM_COUNT.minus(record.value2()))
                        .where(SOURCE.ID.eq(record.value1()))
                        .execute();
            });
        }

        // Delete source items.
        Metrics.measure("Delete source items", () -> {
            context
                    .deleteFrom(SOURCE_ITEM)
                    .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId))
                    .execute();
        });

        // Delete forward records.
        Metrics.measure("Delete forward records", () -> {
            context
                    .delete(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(aggregateId))
                    .execute();
        });

        // Delete aggregate.
        Metrics.measure("Delete aggregate", () -> {
            context
                    .deleteFrom(AGGREGATE)
                    .where(AGGREGATE.ID.eq(aggregateId))
                    .execute();
        });
    }

    public int countForwardAggregates() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, FORWARD_AGGREGATE));
    }

    @Override
    public void flush() {
        recordQueue.flush();
        retryRecordQueue.flush();
    }
}
