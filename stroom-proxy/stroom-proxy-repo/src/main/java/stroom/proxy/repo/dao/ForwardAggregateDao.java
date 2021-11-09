package stroom.proxy.repo.dao;

import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.ForwardAggregate;
import stroom.proxy.repo.ForwardUrl;
import stroom.proxy.repo.WorkQueue;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class ForwardAggregateDao {

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
    private final AtomicLong forwardAggregateId = new AtomicLong();
    private WorkQueue newQueue;
    private WorkQueue retryQueue;

    @Inject
    ForwardAggregateDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        newQueue = WorkQueue.createWithJooq(jooq, FORWARD_AGGREGATE, FORWARD_AGGREGATE.NEW_POSITION);
        retryQueue = WorkQueue.createWithJooq(jooq, FORWARD_AGGREGATE, FORWARD_AGGREGATE.RETRY_POSITION);
        final long maxForwardAggregateRecordId = jooq
                .getMaxId(FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID).orElse(0L);
        forwardAggregateId.set(maxForwardAggregateRecordId);
    }

    public void clear() {
        jooq.deleteAll(FORWARD_AGGREGATE);
        jooq.checkEmpty(FORWARD_AGGREGATE);
        init();
    }

    /**
     * Delete all record of failed forward attempts so we can retry forwarding.
     *
     * @return The number of rows deleted.
     */
    public int deleteFailedForwards() {
        return jooq.underLock(() ->
                jooq.contextResult(context -> context
                        .deleteFrom(FORWARD_AGGREGATE)
                        .where(FORWARD_AGGREGATE.SUCCESS.isFalse())
                        .execute()));
    }

    /**
     * Gets the current forwarding state for the supplied aggregate id.
     *
     * @param aggregateId The aggregateId.
     * @return A map of forward URL ids to success state.
     */
    public Map<Integer, Boolean> getForwardingState(final long aggregateId) {
        return jooq.contextResult(context -> context
                        .select(FORWARD_AGGREGATE.FK_FORWARD_URL_ID, FORWARD_AGGREGATE.SUCCESS)
                        .from(FORWARD_AGGREGATE)
                        .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(aggregateId))
                        .fetch()
                        .stream())
                .collect(Collectors.toMap(Record2::value1, Record2::value2));
    }

    public void addNewForwardAggregates(final List<ForwardUrl> newForwardUrls) {
        if (newForwardUrls.size() > 0) {
            jooq.context(context -> {
                try (Stream<Record1<Long>> stream = context
                        .select(AGGREGATE.ID)
                        .from(AGGREGATE)
                        .where(NEW_AGGREGATE_CONDITION)
                        .stream()) {
                    stream.forEach(r -> createForwardAggregates(r.get(AGGREGATE.ID), newForwardUrls));
                }
            });
        }
    }

    public void removeOldForwardAggregates(final List<ForwardUrl> oldForwardUrls) {
        if (oldForwardUrls.size() > 0) {
            final List<Integer> oldIdList = oldForwardUrls
                    .stream()
                    .map(ForwardUrl::getId)
                    .collect(Collectors.toList());

            jooq.underLock(() -> {
                jooq.transaction(context -> {
                    context
                            .deleteFrom(FORWARD_AGGREGATE)
                            .where(FORWARD_AGGREGATE.FK_FORWARD_URL_ID.in(oldIdList))
                            .execute();

                    try (Stream<Record1<Long>> stream = context
                            .select(AGGREGATE.ID)
                            .where(DELETE_AGGREGATE_CONDITION)
                            .stream()) {
                        stream.forEach(r -> deleteAggregate(context, r.get(AGGREGATE.ID)));
                    }
                });
                return null;
            });
        }
    }

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    public void createForwardAggregates(final long aggregateId,
                                        final List<ForwardUrl> forwardUrls) {
        jooq.underLock(() -> {
            newQueue.put(writePos ->
                    jooq.transaction(context -> {
                        for (final ForwardUrl forwardUrl : forwardUrls) {
                            final long id = forwardAggregateId.incrementAndGet();
                            context
                                    .insertInto(
                                            FORWARD_AGGREGATE,
                                            FORWARD_AGGREGATE.ID,
                                            FORWARD_AGGREGATE.UPDATE_TIME_MS,
                                            FORWARD_AGGREGATE.FK_FORWARD_URL_ID,
                                            FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                                            FORWARD_AGGREGATE.SUCCESS,
                                            FORWARD_AGGREGATE.NEW_POSITION)
                                    .values(id,
                                            System.currentTimeMillis(),
                                            forwardUrl.getId(),
                                            aggregateId,
                                            false,
                                            writePos.incrementAndGet())
                                    .execute();
                        }

                        // Remove the queue position from the aggregate so we don't try and create forwarders again.
                        context
                                .update(AGGREGATE)
                                .setNull(AGGREGATE.NEW_POSITION)
                                .where(AGGREGATE.ID.eq(aggregateId))
                                .execute();
                    }));
            return null;
        });
    }

    public Optional<ForwardAggregate> getNewForwardAggregate() {
        return getForwardAggregate(newQueue, FORWARD_AGGREGATE.NEW_POSITION);
    }

    public Optional<ForwardAggregate> getRetryForwardAggregate() {
        return getForwardAggregate(retryQueue, FORWARD_AGGREGATE.RETRY_POSITION);
    }

    private Optional<ForwardAggregate> getForwardAggregate(final WorkQueue workQueue,
                                                           final Field<Long> positionField) {
        return workQueue.get(position ->
                getForwardAggregateAtQueuePosition(position, positionField));
    }

    public Optional<ForwardAggregate> getNewForwardAggregate(final long timeout,
                                                             final TimeUnit timeUnit) {
        return getForwardAggregate(newQueue, FORWARD_AGGREGATE.NEW_POSITION, timeout, timeUnit);
    }

    public Optional<ForwardAggregate> getRetryForwardAggregate(final long timeout,
                                                               final TimeUnit timeUnit) {
        return getForwardAggregate(retryQueue, FORWARD_AGGREGATE.RETRY_POSITION, timeout, timeUnit);
    }

    private Optional<ForwardAggregate> getForwardAggregate(final WorkQueue workQueue,
                                                           final Field<Long> positionField,
                                                           final long timeout,
                                                           final TimeUnit timeUnit) {
        return workQueue.get(position ->
                getForwardAggregateAtQueuePosition(position, positionField), timeout, timeUnit);
    }

    private Optional<ForwardAggregate> getForwardAggregateAtQueuePosition(final long position,
                                                                          final Field<Long> positionField) {
        return jooq.contextResult(context -> context
                        .select(FORWARD_AGGREGATE.ID,
                                FORWARD_AGGREGATE.UPDATE_TIME_MS,
                                FORWARD_AGGREGATE.FK_FORWARD_URL_ID,
                                FORWARD_URL.URL,
                                AGGREGATE.FEED_NAME,
                                AGGREGATE.TYPE_NAME,
                                FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                                FORWARD_AGGREGATE.SUCCESS,
                                FORWARD_AGGREGATE.ERROR,
                                FORWARD_AGGREGATE.TRIES)
                        .from(FORWARD_AGGREGATE)
                        .join(FORWARD_URL).on(FORWARD_URL.ID.eq(FORWARD_AGGREGATE.FK_FORWARD_URL_ID))
                        .join(AGGREGATE).on(AGGREGATE.ID.eq(FORWARD_AGGREGATE.FK_AGGREGATE_ID))
                        .where(positionField.eq(position))
                        .orderBy(FORWARD_AGGREGATE.ID)
                        .fetchOptional())
                .map(r -> {
                    final ForwardUrl forwardUrl = new ForwardUrl(r.get(FORWARD_AGGREGATE.FK_FORWARD_URL_ID),
                            r.get(FORWARD_URL.URL));
                    final Aggregate aggregate = new Aggregate(
                            r.get(FORWARD_AGGREGATE.FK_AGGREGATE_ID),
                            r.get(AGGREGATE.FEED_NAME),
                            r.get(AGGREGATE.TYPE_NAME));
                    return new ForwardAggregate(
                            r.get(FORWARD_AGGREGATE.ID),
                            r.get(FORWARD_AGGREGATE.UPDATE_TIME_MS),
                            aggregate,
                            forwardUrl,
                            r.get(FORWARD_AGGREGATE.SUCCESS),
                            r.get(FORWARD_AGGREGATE.ERROR),
                            r.get(FORWARD_AGGREGATE.TRIES));
                });
    }

    public void update(final ForwardAggregate forwardAggregate) {
        if (forwardAggregate.isSuccess()) {
            final long aggregateId = forwardAggregate.getAggregate().getId();

            // Mark success and see if we can delete this record and cascade.
            jooq.underLock(() -> {
                jooq.transaction(context -> {
                    // We finished forwarding an aggregate so delete all related forward aggregate records.
                    updateForwardAggregate(context, forwardAggregate, null);

                    final Condition condition = FORWARD_AGGREGATE.FK_AGGREGATE_ID
                            .eq(forwardAggregate.getAggregate().getId())
                            .and(FORWARD_AGGREGATE.SUCCESS.ne(true));
                    final int remainingForwards = context.fetchCount(FORWARD_AGGREGATE, condition);
                    if (remainingForwards == 0) {
                        deleteAggregate(context, aggregateId);
                    }
                });
                return null;
            });

        } else {
            // Update and schedule for retry.
            jooq.underLock(() -> {
                newQueue.put(writePos ->
                        jooq.context(context ->
                                updateForwardAggregate(context, forwardAggregate, writePos.incrementAndGet())));

                return null;
            });
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
                .set(FORWARD_AGGREGATE.RETRY_POSITION, retryPosition)
                .where(FORWARD_AGGREGATE.ID.eq(forwardAggregate.getId()))
                .execute();
    }

    private void deleteAggregate(final DSLContext context, final long aggregateId) {
        // Delete forward records.
        context
                .delete(FORWARD_AGGREGATE)
                .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(aggregateId))
                .execute();

        // Delete source entries.
        context
                .deleteFrom(SOURCE_ENTRY)
                .where(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.in(
                        context
                                .select(SOURCE_ITEM.ID)
                                .from(SOURCE_ITEM)
                                .where(SOURCE_ITEM.AGGREGATE_ID.eq(aggregateId)))
                )
                .execute();

        // Delete source items.
        context
                .deleteFrom(SOURCE_ITEM)
                .where(SOURCE_ITEM.AGGREGATE_ID.eq(aggregateId))
                .execute();

        // Delete aggregate.
        context
                .deleteFrom(AGGREGATE)
                .where(AGGREGATE.ID.eq(aggregateId))
                .execute();
    }

    public int countForwardAggregates() {
        return jooq.count(FORWARD_AGGREGATE);
    }
}
