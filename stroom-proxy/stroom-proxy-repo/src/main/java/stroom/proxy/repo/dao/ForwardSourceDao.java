package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.ForwardSource;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.db.jooq.tables.records.ForwardSourceRecord;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BindWriteQueue;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.util.shared.Flushable;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
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
import static stroom.proxy.repo.db.jooq.tables.ForwardSource.FORWARD_SOURCE;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@Singleton
public class ForwardSourceDao implements Flushable {

    private static final Field<?>[] FORWARD_SOURCE_COLUMNS = new Field<?>[]{
            FORWARD_SOURCE.ID,
            FORWARD_SOURCE.UPDATE_TIME_MS,
            FORWARD_SOURCE.FK_FORWARD_DEST_ID,
            FORWARD_SOURCE.FK_SOURCE_ID,
            FORWARD_SOURCE.SUCCESS,
            FORWARD_SOURCE.NEW_POSITION};

    private static final Condition NEW_SOURCE_CONDITION =
            SOURCE.NEW_POSITION.isNull().andExists(DSL
                    .select(FORWARD_SOURCE.ID)
                    .from(FORWARD_SOURCE)
                    .where(FORWARD_SOURCE.FK_SOURCE_ID.eq(SOURCE.ID)));

    private final SqliteJooqHelper jooq;
    private final ProxyDbConfig dbConfig;
    private final AtomicLong forwardAggregateId = new AtomicLong();


    private final AtomicLong forwardAggregateNewPosition = new AtomicLong();
    private final AtomicLong forwardAggregateRetryPosition = new AtomicLong();

    private final RecordQueue recordQueue;
    private final OperationWriteQueue aggregateUpdateQueue;
    private final BindWriteQueue forwardAggregateWriteQueue;
    private final ReadQueue<ForwardSource> forwardAggregateReadQueue;


    private final RecordQueue retryRecordQueue;
    private final OperationWriteQueue retryUpdateQueue;
    private final ReadQueue<ForwardSource> retryReadQueue;

    @Inject
    ForwardSourceDao(final SqliteJooqHelper jooq,
                     final ProxyDbConfig dbConfig) {
        this.jooq = jooq;
        this.dbConfig = dbConfig;
        init();

        aggregateUpdateQueue = new OperationWriteQueue();
        forwardAggregateWriteQueue = new BindWriteQueue(FORWARD_SOURCE, FORWARD_SOURCE_COLUMNS);
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

    private long readNew(final long currentReadPos, final long limit, List<ForwardSource> readQueue) {
        return read(currentReadPos, limit, readQueue, FORWARD_SOURCE.NEW_POSITION);
    }

    private long readRetry(final long currentReadPos, final long limit, List<ForwardSource> readQueue) {
        return read(currentReadPos, limit, readQueue, FORWARD_SOURCE.RETRY_POSITION);
    }

    private long read(final long currentReadPos,
                      final long limit,
                      final List<ForwardSource> readQueue,
                      final TableField<ForwardSourceRecord, Long> positionField) {
        final AtomicLong pos = new AtomicLong(currentReadPos);
        jooq.readOnlyTransactionResult(context -> context
                        .select(FORWARD_SOURCE.ID,
                                FORWARD_SOURCE.UPDATE_TIME_MS,
                                FORWARD_SOURCE.FK_FORWARD_DEST_ID,
                                FORWARD_SOURCE.FK_SOURCE_ID,
                                FORWARD_SOURCE.SUCCESS,
                                FORWARD_SOURCE.ERROR,
                                FORWARD_SOURCE.TRIES,
                                positionField,
                                FORWARD_DEST.NAME,
                                SOURCE.FILE_STORE_ID,
                                SOURCE.FK_FEED_ID)
                        .from(FORWARD_SOURCE)
                        .join(FORWARD_DEST).on(FORWARD_DEST.ID.eq(FORWARD_SOURCE.FK_FORWARD_DEST_ID))
                        .join(SOURCE).on(SOURCE.ID.eq(FORWARD_SOURCE.FK_SOURCE_ID))
                        .where(positionField.isNotNull())
                        .and(positionField.gt(currentReadPos))
                        .orderBy(positionField)
                        .limit(limit)
                        .fetch())
                .forEach(r -> {
                    pos.set(r.get(positionField));
                    final ForwardDest forwardDest = new ForwardDest(r.get(FORWARD_AGGREGATE.FK_FORWARD_DEST_ID),
                            r.get(FORWARD_DEST.NAME));
                    final RepoSource source = new RepoSource(
                            r.get(FORWARD_SOURCE.FK_SOURCE_ID),
                            r.get(SOURCE.FILE_STORE_ID),
                            r.get(SOURCE.FK_FEED_ID));
                    final ForwardSource forwardSource = new ForwardSource(
                            r.get(FORWARD_SOURCE.ID),
                            r.get(FORWARD_SOURCE.UPDATE_TIME_MS),
                            source,
                            forwardDest,
                            r.get(FORWARD_SOURCE.SUCCESS),
                            r.get(FORWARD_SOURCE.ERROR),
                            r.get(FORWARD_SOURCE.TRIES));
                    readQueue.add(forwardSource);
                });
        return pos.get();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            forwardAggregateId.set(JooqUtil
                    .getMaxId(context, FORWARD_SOURCE, FORWARD_SOURCE.ID)
                    .orElse(0L));

            forwardAggregateNewPosition.set(JooqUtil
                    .getMaxId(context, FORWARD_SOURCE, FORWARD_SOURCE.NEW_POSITION)
                    .orElse(0L));

            forwardAggregateRetryPosition.set(JooqUtil
                    .getMaxId(context, FORWARD_SOURCE, FORWARD_SOURCE.RETRY_POSITION)
                    .orElse(0L));
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, FORWARD_SOURCE);
            JooqUtil.checkEmpty(context, FORWARD_SOURCE);
        });
        recordQueue.clear();
        retryRecordQueue.clear();
        init();
    }

//    /**
//     * Delete all record of failed forward attempts so we can retry forwarding.
//     *
//     * @return The number of rows deleted.
//     */
//    public int deleteFailedForwards() {
//        return jooq.transactionResult(context -> context
//                .deleteFrom(FORWARD_SOURCE)
//                .where(FORWARD_SOURCE.SUCCESS.isFalse())
//                .execute());
//    }
//
//    /**
//     * Gets the current forwarding state for the supplied source id.
//     *
//     * @param sourceId The sourceId.
//     * @return A map of forward URL ids to success state.
//     */
//    public Map<Integer, Boolean> getForwardingState(final long sourceId) {
//        return jooq.readOnlyTransactionResult(context -> context
//                        .select(FORWARD_SOURCE.FK_FORWARD_DEST_ID, FORWARD_SOURCE.SUCCESS)
//                        .from(FORWARD_SOURCE)
//                        .where(FORWARD_SOURCE.FK_SOURCE_ID.eq(sourceId))
//                        .fetch())
//                .stream()
//                .collect(Collectors.toMap(Record2::value1, Record2::value2));
//    }

    /**
     * Add forward sources for any new dests that have been added since the application last ran.
     *
     * @param newForwardDests New dests to add forward aggregate entries for.
     */
    public void addNewForwardSources(final List<ForwardDest> newForwardDests) {
        if (newForwardDests.size() > 0) {
            final AtomicLong minId = new AtomicLong();
            final int batchSize = dbConfig.getBatchSize();
            boolean full = true;
            while (full) {
                final List<RepoSource> sources = new ArrayList<>();
                jooq.readOnlyTransactionResult(context -> context
                                .select(SOURCE.ID,
                                        SOURCE.FILE_STORE_ID,
                                        SOURCE.FK_FEED_ID)
                                .from(SOURCE)
                                .where(NEW_SOURCE_CONDITION)
                                .and(SOURCE.ID.gt(minId.get()))
                                .orderBy(SOURCE.ID)
                                .limit(batchSize)
                                .fetch())
                        .forEach(r -> {
                            minId.set(r.get(AGGREGATE.ID));
                            final RepoSource source = new RepoSource(
                                    r.get(SOURCE.ID),
                                    r.get(SOURCE.FILE_STORE_ID),
                                    r.get(SOURCE.FK_FEED_ID));
                            sources.add(source);
                        });

                final Batch<RepoSource> batch = new Batch<>(sources, sources.size() == batchSize);
                createForwardSources(batch, newForwardDests);
                full = batch.full();
            }
        }
    }

    public void removeOldForwardSources(final List<ForwardDest> oldForwardDests) {
        if (oldForwardDests.size() > 0) {
            final List<Integer> oldIdList = oldForwardDests
                    .stream()
                    .map(ForwardDest::getId)
                    .collect(Collectors.toList());

            jooq.transaction(context -> context
                    .deleteFrom(FORWARD_SOURCE)
                    .where(FORWARD_SOURCE.FK_FORWARD_DEST_ID.in(oldIdList))
                    .execute());
        }
    }

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    public void createForwardSources(final Batch<RepoSource> sources,
                                     final List<ForwardDest> forwardDests) {
        recordQueue.add(() -> {
            for (final RepoSource source : sources.list()) {
                for (final ForwardDest forwardDest : forwardDests) {
                    final Object[] row = new Object[FORWARD_SOURCE_COLUMNS.length];
                    row[0] = forwardAggregateId.incrementAndGet();
                    row[1] = System.currentTimeMillis();
                    row[2] = forwardDest.getId();
                    row[3] = source.id();
                    row[4] = false;
                    row[5] = forwardAggregateNewPosition.incrementAndGet();
                    forwardAggregateWriteQueue.add(row);
                }

                // Remove the queue position from the source so we don't try and create forwarders again.
                aggregateUpdateQueue.add(context -> context
                        .update(SOURCE)
                        .setNull(SOURCE.NEW_POSITION)
                        .where(SOURCE.ID.eq(source.id()))
                        .execute());
            }
        });
    }

    public Batch<ForwardSource> getNewForwardSources() {
        return recordQueue.getBatch(forwardAggregateReadQueue);
    }

    public Batch<ForwardSource> getRetryForwardSources() {
        return retryRecordQueue.getBatch(retryReadQueue);
    }

//    private Optional<ForwardSource> getForwardSource(final WorkQueue workQueue,
//                                                     final Field<Long> positionField) {
//        return workQueue.get(position ->
//                getForwardSourceAtQueuePosition(position, positionField));
//    }

    public Batch<ForwardSource> getNewForwardSources(final long timeout,
                                                     final TimeUnit timeUnit) {
        return recordQueue.getBatch(forwardAggregateReadQueue, timeout, timeUnit);
    }

    public Batch<ForwardSource> getRetryForwardSources(final long timeout,
                                                       final TimeUnit timeUnit) {
        return retryRecordQueue.getBatch(retryReadQueue, timeout, timeUnit);
    }

//    private Optional<ForwardSource> getForwardSource(final WorkQueue workQueue,
//                                                     final Field<Long> positionField,
//                                                     final long timeout,
//                                                     final TimeUnit timeUnit) {
//        return workQueue.get(position ->
//                getForwardSourceAtQueuePosition(position, positionField), timeout, timeUnit);
//    }
//
//    private Optional<ForwardSource> getForwardSourceAtQueuePosition(final long position,
//                                                                    final Field<Long> positionField) {
//        return jooq.readOnlyTransactionResult(context -> context
//                        .select(FORWARD_SOURCE.ID,
//                                FORWARD_SOURCE.UPDATE_TIME_MS,
//                                FORWARD_SOURCE.FK_FORWARD_DEST_ID,
//                                FORWARD_DEST.NAME,
//                                SOURCE.FILE_STORE_ID,
//                                SOURCE.FEED_NAME,
//                                SOURCE.TYPE_NAME,
//                                SOURCE.LAST_MODIFIED_TIME_MS,
//                                FORWARD_SOURCE.FK_SOURCE_ID,
//                                FORWARD_SOURCE.SUCCESS,
//                                FORWARD_SOURCE.ERROR,
//                                FORWARD_SOURCE.TRIES)
//                        .from(FORWARD_SOURCE)
//                        .join(FORWARD_DEST).on(FORWARD_DEST.ID.eq(FORWARD_SOURCE.FK_FORWARD_DEST_ID))
//                        .join(SOURCE).on(SOURCE.ID.eq(FORWARD_SOURCE.FK_SOURCE_ID))
//                        .where(positionField.eq(position))
//                        .orderBy(FORWARD_SOURCE.ID)
//                        .fetchOptional())
//                .map(r -> {
//                    final ForwardUrl forwardUrl = new ForwardUrl(r.get(FORWARD_SOURCE.FK_FORWARD_DEST_ID),
//                            r.get(FORWARD_DEST.NAME));
//                    final RepoSource source = new RepoSource(
//                            r.get(FORWARD_SOURCE.FK_SOURCE_ID),
//                            r.get(SOURCE.FILE_STORE_ID),
//                            r.get(SOURCE.FEED_NAME),
//                            r.get(SOURCE.TYPE_NAME),
//                            r.get(SOURCE.LAST_MODIFIED_TIME_MS));
//                    return new ForwardSource(
//                            r.get(FORWARD_SOURCE.ID),
//                            r.get(FORWARD_SOURCE.UPDATE_TIME_MS),
//                            source,
//                            forwardUrl,
//                            r.get(FORWARD_SOURCE.SUCCESS),
//                            r.get(FORWARD_SOURCE.ERROR),
//                            r.get(FORWARD_SOURCE.TRIES));
//                });
//    }

    public void update(final ForwardSource forwardSource) {
        if (forwardSource.isSuccess()) {
            final long sourceId = forwardSource.getSource().id();

            // Mark success and see if we can delete this record and cascade.
            jooq.transaction(context -> {
                // We finished forwarding a source so delete all related forward aggregate records.
                updateForwardSource(context, forwardSource, null);

                final Condition condition = FORWARD_SOURCE.FK_SOURCE_ID
                        .eq(forwardSource.getSource().id())
                        .and(FORWARD_SOURCE.SUCCESS.ne(true));
                final int remainingForwards = context.fetchCount(FORWARD_SOURCE, condition);
                if (remainingForwards == 0) {
                    deleteForwardSource(context, sourceId);
                }
            });
        } else {
            // Update and schedule for retry.
            retryRecordQueue.add(() ->
                    retryUpdateQueue.add(context ->
                            updateForwardSource(
                                    context,
                                    forwardSource,
                                    forwardAggregateRetryPosition.incrementAndGet())));
        }
    }

    private void updateForwardSource(final DSLContext context,
                                     final ForwardSource forwardSource,
                                     final Long retryPosition) {
        context
                .update(FORWARD_SOURCE)
                .set(FORWARD_SOURCE.UPDATE_TIME_MS, forwardSource.getUpdateTimeMs())
                .set(FORWARD_SOURCE.SUCCESS, forwardSource.isSuccess())
                .set(FORWARD_SOURCE.ERROR, forwardSource.getError())
                .setNull(FORWARD_SOURCE.NEW_POSITION)
                .set(FORWARD_SOURCE.TRIES, forwardSource.getTries())
                .set(FORWARD_SOURCE.RETRY_POSITION, retryPosition)
                .where(FORWARD_SOURCE.ID.eq(forwardSource.getId()))
                .execute();
    }

    private void deleteForwardSource(final DSLContext context, final long sourceId) {
        // Mark source as forwarded by setting examined with 0 items.
        context
                .update(SOURCE)
                .set(SOURCE.EXAMINED, true)
                .set(SOURCE.ITEM_COUNT, 0)
                .setNull(SOURCE.NEW_POSITION)
                .where(SOURCE.ID.eq(sourceId))
                .execute();

        // Delete forward records.
        context
                .delete(FORWARD_SOURCE)
                .where(FORWARD_SOURCE.FK_SOURCE_ID.eq(sourceId))
                .execute();
    }

    public int countForwardSource() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, FORWARD_SOURCE));
    }

    @Override
    public void flush() {
        recordQueue.flush();
        retryRecordQueue.flush();
    }
}
