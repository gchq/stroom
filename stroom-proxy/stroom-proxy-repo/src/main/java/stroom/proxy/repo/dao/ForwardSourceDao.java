package stroom.proxy.repo.dao;

import stroom.proxy.repo.ForwardSource;
import stroom.proxy.repo.ForwardUrl;
import stroom.proxy.repo.RepoSource;
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

import static stroom.proxy.repo.db.jooq.tables.ForwardSource.FORWARD_SOURCE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@Singleton
public class ForwardSourceDao {

    private static final Condition NEW_SOURCE_CONDITION =
            SOURCE.NEW_POSITION.isNull().andExists(DSL
                    .select(FORWARD_SOURCE.ID)
                    .from(FORWARD_SOURCE)
                    .where(FORWARD_SOURCE.SOURCE_ID.eq(SOURCE.ID)));

    private final SqliteJooqHelper jooq;
    private final AtomicLong forwardSourceId = new AtomicLong();
    private WorkQueue newQueue;
    private WorkQueue retryQueue;

    @Inject
    ForwardSourceDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        newQueue = WorkQueue.createWithJooq(jooq, FORWARD_SOURCE, FORWARD_SOURCE.NEW_POSITION);
        retryQueue = WorkQueue.createWithJooq(jooq, FORWARD_SOURCE, FORWARD_SOURCE.RETRY_POSITION);
        final long maxForwardSourceRecordId = jooq
                .getMaxId(FORWARD_SOURCE, FORWARD_SOURCE.ID).orElse(0L);
        forwardSourceId.set(maxForwardSourceRecordId);
    }

    public void clear() {
        jooq.deleteAll(FORWARD_SOURCE);
        jooq.checkEmpty(FORWARD_SOURCE);
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
                        .deleteFrom(FORWARD_SOURCE)
                        .where(FORWARD_SOURCE.SUCCESS.isFalse())
                        .execute()));
    }

    /**
     * Gets the current forwarding state for the supplied source id.
     *
     * @param sourceId The sourceId.
     * @return A map of forward URL ids to success state.
     */
    public Map<Integer, Boolean> getForwardingState(final long sourceId) {
        return jooq.contextResult(context -> context
                .select(FORWARD_SOURCE.FK_FORWARD_URL_ID, FORWARD_SOURCE.SUCCESS)
                .from(FORWARD_SOURCE)
                .where(FORWARD_SOURCE.SOURCE_ID.eq(sourceId))
                .fetch()
                .stream()
                .collect(Collectors.toMap(Record2::value1, Record2::value2)));
    }

    public void addNewForwardSources(final List<ForwardUrl> newForwardUrls) {
        if (newForwardUrls.size() > 0) {
            jooq.context(context -> {
                try (Stream<Record1<Long>> stream = context
                        .select(SOURCE.ID)
                        .from(SOURCE)
                        .where(NEW_SOURCE_CONDITION)
                        .stream()) {
                    stream.forEach(r -> createForwardSources(r.get(SOURCE.ID), newForwardUrls));
                }
            });
        }
    }

    public void removeOldForwardSources(final List<ForwardUrl> oldForwardUrls) {
        if (oldForwardUrls.size() > 0) {
            final List<Integer> oldIdList = oldForwardUrls
                    .stream()
                    .map(ForwardUrl::getId)
                    .collect(Collectors.toList());

            jooq.underLock(() -> {
                jooq.transaction(context -> context
                        .deleteFrom(FORWARD_SOURCE)
                        .where(FORWARD_SOURCE.FK_FORWARD_URL_ID.in(oldIdList))
                        .execute());
                return null;
            });
        }
    }

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    public void createForwardSources(final long sourceId,
                                     final List<ForwardUrl> forwardUrls) {
        jooq.underLock(() -> {
            newQueue.put(writePos ->
                    jooq.transaction(context -> {
                        for (final ForwardUrl forwardUrl : forwardUrls) {
                            final long id = forwardSourceId.incrementAndGet();
                            context
                                    .insertInto(
                                            FORWARD_SOURCE,
                                            FORWARD_SOURCE.ID,
                                            FORWARD_SOURCE.UPDATE_TIME_MS,
                                            FORWARD_SOURCE.FK_FORWARD_URL_ID,
                                            FORWARD_SOURCE.SOURCE_ID,
                                            FORWARD_SOURCE.SUCCESS,
                                            FORWARD_SOURCE.NEW_POSITION)
                                    .values(id,
                                            System.currentTimeMillis(),
                                            forwardUrl.getId(),
                                            sourceId,
                                            false,
                                            writePos.incrementAndGet())
                                    .execute();
                        }

                        // Remove the queue position from the aggregate, so we don't try and create forwarders again.
                        context
                                .update(SOURCE)
                                .setNull(SOURCE.NEW_POSITION)
                                .where(SOURCE.ID.eq(sourceId))
                                .execute();
                    }));
            return null;
        });
    }

    public Optional<ForwardSource> getNewForwardSource() {
        return getForwardSource(newQueue, FORWARD_SOURCE.NEW_POSITION);
    }

    public Optional<ForwardSource> getRetryForwardSource() {
        return getForwardSource(retryQueue, FORWARD_SOURCE.RETRY_POSITION);
    }

    private Optional<ForwardSource> getForwardSource(final WorkQueue workQueue,
                                                     final Field<Long> positionField) {
        return workQueue.get(position ->
                getForwardSourceAtQueuePosition(position, positionField));
    }

    public Optional<ForwardSource> getNewForwardSource(final long timeout,
                                                       final TimeUnit timeUnit) {
        return getForwardSource(newQueue, FORWARD_SOURCE.NEW_POSITION, timeout, timeUnit);
    }

    public Optional<ForwardSource> getRetryForwardSource(final long timeout,
                                                         final TimeUnit timeUnit) {
        return getForwardSource(retryQueue, FORWARD_SOURCE.RETRY_POSITION, timeout, timeUnit);
    }

    private Optional<ForwardSource> getForwardSource(final WorkQueue workQueue,
                                                     final Field<Long> positionField,
                                                     final long timeout,
                                                     final TimeUnit timeUnit) {
        return workQueue.get(position ->
                getForwardSourceAtQueuePosition(position, positionField), timeout, timeUnit);
    }

    private Optional<ForwardSource> getForwardSourceAtQueuePosition(final long position,
                                                                    final Field<Long> positionField) {
        return jooq.contextResult(context -> context
                        .select(FORWARD_SOURCE.ID,
                                FORWARD_SOURCE.UPDATE_TIME_MS,
                                FORWARD_SOURCE.FK_FORWARD_URL_ID,
                                FORWARD_URL.URL,
                                SOURCE.PATH,
                                SOURCE.FEED_NAME,
                                SOURCE.TYPE_NAME,
                                SOURCE.LAST_MODIFIED_TIME_MS,
                                FORWARD_SOURCE.SOURCE_ID,
                                FORWARD_SOURCE.SUCCESS,
                                FORWARD_SOURCE.ERROR,
                                FORWARD_SOURCE.TRIES)
                        .from(FORWARD_SOURCE)
                        .join(FORWARD_URL).on(FORWARD_URL.ID.eq(FORWARD_SOURCE.FK_FORWARD_URL_ID))
                        .join(SOURCE).on(SOURCE.ID.eq(FORWARD_SOURCE.SOURCE_ID))
                        .where(positionField.eq(position))
                        .orderBy(FORWARD_SOURCE.ID)
                        .fetchOptional())
                .map(r -> {
                    final ForwardUrl forwardUrl = new ForwardUrl(r.get(FORWARD_SOURCE.FK_FORWARD_URL_ID),
                            r.get(FORWARD_URL.URL));
                    final RepoSource source = new RepoSource(
                            r.get(FORWARD_SOURCE.SOURCE_ID),
                            r.get(SOURCE.PATH),
                            r.get(SOURCE.FEED_NAME),
                            r.get(SOURCE.TYPE_NAME),
                            r.get(SOURCE.LAST_MODIFIED_TIME_MS));
                    return new ForwardSource(
                            r.get(FORWARD_SOURCE.ID),
                            r.get(FORWARD_SOURCE.UPDATE_TIME_MS),
                            source,
                            forwardUrl,
                            r.get(FORWARD_SOURCE.SUCCESS),
                            r.get(FORWARD_SOURCE.ERROR),
                            r.get(FORWARD_SOURCE.TRIES));
                });
    }


    public void update(final ForwardSource forwardSource) {
        if (forwardSource.isSuccess()) {
            final long sourceId = forwardSource.getSource().getId();

            // Mark success and see if we can delete this record and cascade.
            jooq.underLock(() -> {
                jooq.transaction(context -> {
                    // We finished forwarding a source so delete all related forward aggregate records.
                    updateForwardSource(context, forwardSource, null);

                    final Condition condition = FORWARD_SOURCE.SOURCE_ID
                            .eq(forwardSource.getSource().getId())
                            .and(FORWARD_SOURCE.SUCCESS.ne(true));
                    final int remainingForwards = context.fetchCount(FORWARD_SOURCE, condition);
                    if (remainingForwards == 0) {
                        // Delete forward records.
                        context
                                .delete(FORWARD_SOURCE)
                                .where(FORWARD_SOURCE.SOURCE_ID.eq(sourceId))
                                .execute();

                        // Mark source as forwarded.
                        context
                                .update(SOURCE)
                                .set(SOURCE.FORWARDED, true)
                                .where(SOURCE.ID.eq(sourceId))
                                .execute();
                    }
                });
                return null;
            });

        } else {
            // Update and schedule for retry.
            jooq.underLock(() -> {
                newQueue.put(writePos ->
                        jooq.context(context ->
                                updateForwardSource(context, forwardSource, writePos.incrementAndGet())));

                return null;
            });
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

    public int countForwardSource() {
        return jooq.count(FORWARD_SOURCE);
    }
}
