package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoDbConnProvider;

import org.jooq.Record2;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.ForwardSource.FORWARD_SOURCE;

@Singleton
public class ForwardSourceDao {

    private final SqliteJooqHelper jooq;
    private final SourceDao sourceDao;
    private final AtomicLong forwardSourceId = new AtomicLong();

    @Inject
    ForwardSourceDao(final ProxyRepoDbConnProvider connProvider,
                     final SourceDao sourceDao) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.sourceDao = sourceDao;
        init();
    }

    private void init() {
        final long maxForwardSourceRecordId = jooq
                .getMaxId(FORWARD_SOURCE, FORWARD_SOURCE.ID).orElse(0L);
        forwardSourceId.set(maxForwardSourceRecordId);
    }

    /**
     * Delete all record of failed forward attempts so we can retry forwarding.
     *
     * @return The number of rows deleted.
     */
    public int deleteFailedForwards() {
        return jooq.contextResult(context -> context
                .deleteFrom(FORWARD_SOURCE)
                .where(FORWARD_SOURCE.SUCCESS.isFalse())
                .execute());
    }

    /**
     * Delete all forward source records.
     *
     * @return The number of rows deleted.
     */
    public int deleteAll() {
        return jooq.contextResult(context -> context
                .deleteFrom(FORWARD_SOURCE)
                .execute());
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
                .where(FORWARD_SOURCE.FK_SOURCE_ID.eq(sourceId))
                .fetch()
                .stream()
                .collect(Collectors.toMap(Record2::value1, Record2::value2)));
    }

    /**
     * Create a record of the fact that we forwarded a source or at least tried to.
     */
    public void createForwardSourceRecord(final int forwardUrlId,
                                          final long sourceId,
                                          final boolean success,
                                          final String error) {
        jooq.context(context -> context
                .insertInto(
                        FORWARD_SOURCE,
                        FORWARD_SOURCE.ID,
                        FORWARD_SOURCE.FK_FORWARD_URL_ID,
                        FORWARD_SOURCE.FK_SOURCE_ID,
                        FORWARD_SOURCE.SUCCESS,
                        FORWARD_SOURCE.ERROR)
                .values(forwardSourceId.incrementAndGet(), forwardUrlId, sourceId, success, error)
                .execute());
    }

    public void setForwardSuccess(final long sourceId) {
        jooq.transaction(context -> {
            // We finished forwarding a source so delete all related forward source records.
            context
                    .deleteFrom(FORWARD_SOURCE)
                    .where(FORWARD_SOURCE.FK_SOURCE_ID.equal(sourceId))
                    .execute();

            // Record the source as forwarded so it can be deleted by Cleanup.
            sourceDao.setForwardSuccess(context, sourceId);
        });
    }

    public void clear() {
        deleteAll();
        jooq
                .getMaxId(FORWARD_SOURCE, FORWARD_SOURCE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        init();
    }
}
