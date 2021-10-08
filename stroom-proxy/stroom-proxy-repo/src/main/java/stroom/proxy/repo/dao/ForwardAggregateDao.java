package stroom.proxy.repo.dao;

import stroom.db.util.SqliteJooqHelper;
import stroom.proxy.repo.ProxyRepoDbConnProvider;

import org.jooq.Record2;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;

@Singleton
public class ForwardAggregateDao {

    private final SqliteJooqHelper jooq;
    private final AggregateDao aggregateDao;
    private final AtomicLong forwardAggregateId = new AtomicLong();

    @Inject
    ForwardAggregateDao(final ProxyRepoDbConnProvider connProvider,
                        final AggregateDao aggregateDao) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.aggregateDao = aggregateDao;
        init();
    }

    private void init() {
        final long maxForwardAggregateRecordId = jooq
                .getMaxId(FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID).orElse(0L);
        forwardAggregateId.set(maxForwardAggregateRecordId);
    }

    /**
     * Delete all record of failed forward attempts so we can retry forwarding.
     *
     * @return The number of rows deleted.
     */
    public int deleteFailedForwards() {
        return jooq.contextResult(context -> context
                .deleteFrom(FORWARD_AGGREGATE)
                .where(FORWARD_AGGREGATE.SUCCESS.isFalse())
                .execute());
    }

    public int deleteAll() {
        return jooq.contextResult(context -> context
                .deleteFrom(FORWARD_AGGREGATE)
                .execute());
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

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    public void createForwardAggregateRecord(final int forwardUrlId,
                                             final long aggregateId,
                                             final boolean success,
                                             final String error) {
        jooq.context(context -> context
                .insertInto(
                        FORWARD_AGGREGATE,
                        FORWARD_AGGREGATE.ID,
                        FORWARD_AGGREGATE.FK_FORWARD_URL_ID,
                        FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                        FORWARD_AGGREGATE.SUCCESS,
                        FORWARD_AGGREGATE.ERROR)
                .values(forwardAggregateId.incrementAndGet(), forwardUrlId, aggregateId, success, error)
                .execute());
    }

    public void setForwardSuccess(final long aggregateId) {
        jooq.transaction(context -> {
            // We finished forwarding an aggregate so delete all related forward aggregate records.
            context
                    .deleteFrom(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.equal(aggregateId))
                    .execute();

            // Record the source as forwarded so it can be deleted by Cleanup.
            aggregateDao.setForwardSuccess(context, aggregateId);
        });
    }

    public void clear() {
        deleteAll();
        jooq
                .getMaxId(FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        init();
    }
}
