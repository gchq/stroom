package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.WorkQueue;
import stroom.util.logging.Metrics;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SourceDao {

    private static final Condition DELETE_SOURCE_CONDITION =
            SOURCE.FORWARDED.isTrue().or(
                    SOURCE.EXAMINED.isTrue()
                            .andNotExists(DSL
                                    .select(SOURCE_ITEM.ID)
                                    .from(SOURCE_ITEM)
                                    .where(SOURCE_ITEM.SOURCE_ID.eq(SOURCE.ID))));

    private final SqliteJooqHelper jooq;

    private final AtomicLong sourceRecordId = new AtomicLong();
    private WorkQueue newQueue;

    @Inject
    SourceDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;

        Metrics.startPeriodicReport(10000);

        init();
    }

    private void init() {
        Metrics.measure("SourceDao - init()", () ->
                jooq.readOnlyTransaction(context -> {
                    newQueue = WorkQueue.createWithJooq(context, SOURCE, SOURCE.NEW_POSITION);
                    final long maxSourceRecordId = JooqUtil.getMaxId(context, SOURCE, SOURCE.ID).orElse(0L);
                    sourceRecordId.set(maxSourceRecordId);
                }));
    }

    public void clear() {
        Metrics.measure("SourceDao - clear()", () -> {
            jooq.transaction(context -> {
                JooqUtil.deleteAll(context, SOURCE);
                JooqUtil.checkEmpty(context, SOURCE);
            });
            init();
        });
    }

    public boolean pathExists(final String path) {
        return Metrics.measure("SourceDao - pathExists()", () ->
                jooq.readOnlyTransactionResult(context -> context
                        .fetchExists(
                                context
                                        .selectFrom(SOURCE)
                                        .where(SOURCE.PATH.eq(path))
                        )));
    }

    /**
     * Add a new source to the database.
     * <p>
     * If a new source is successfully added then return it in the optional result. If a source for the supplied path
     * already exists then return an empty optional.
     * <p>
     * This method is synchronized to cope with sources being added via receipt and repo scanning at the same time.
     *
     * @param path               The path of the source to add.
     * @param feedName           The feed name associated with the source.
     * @param typeName           The type name associated with the source.
     * @param lastModifiedTimeMs The last time the source data was modified.
     */
    public void addSource(final String path,
                          final String feedName,
                          final String typeName,
                          final long lastModifiedTimeMs) {
        Metrics.measure("SourceDao - addSource()", () -> {
            // If a source already exists for the supplied path then return an empty optional.
            if (!pathExists(path)) {
                newQueue.put(writePos ->
                        jooq.transaction(context -> {
                            final long sourceId = sourceRecordId.incrementAndGet();
                            context
                                    .insertInto(
                                            SOURCE,
                                            SOURCE.ID,
                                            SOURCE.PATH,
                                            SOURCE.FEED_NAME,
                                            SOURCE.TYPE_NAME,
                                            SOURCE.LAST_MODIFIED_TIME_MS,
                                            SOURCE.NEW_POSITION
                                    )
                                    .values(
                                            sourceId,
                                            path,
                                            feedName,
                                            typeName,
                                            lastModifiedTimeMs,
                                            writePos.incrementAndGet()
                                    )
                                    .execute();
                        }));
            }
        });
    }

    public Optional<RepoSource> getNewSource() {
        return Metrics.measure("SourceDao - getNewSource()", () ->
                newQueue.get(this::getSourceAtPosition));
    }

    public Optional<RepoSource> getNewSource(final long timeout,
                                             final TimeUnit timeUnit) {
        return Metrics.measure("SourceDao - getNewSource2()", () ->
                newQueue.get(this::getSourceAtPosition, timeout, timeUnit));
    }

    public Optional<RepoSource> getSourceAtPosition(final long position) {
        return Metrics.measure("SourceDao - getSourceAtPosition()", () ->
                jooq.readOnlyTransactionResult(context -> context
                                .select(SOURCE.ID,
                                        SOURCE.PATH,
                                        SOURCE.FEED_NAME,
                                        SOURCE.TYPE_NAME,
                                        SOURCE.LAST_MODIFIED_TIME_MS)
                                .from(SOURCE)
                                .where(SOURCE.NEW_POSITION.eq(position))
                                .orderBy(SOURCE.ID)
                                .fetchOptional())
                        .map(r -> new RepoSource(
                                r.get(SOURCE.ID),
                                r.get(SOURCE.PATH),
                                r.get(SOURCE.FEED_NAME),
                                r.get(SOURCE.TYPE_NAME),
                                r.get(SOURCE.LAST_MODIFIED_TIME_MS)
                        )));
    }

    public int countSources() {
        return Metrics.measure("SourceDao - countSources()", () ->
                jooq.readOnlyTransactionResult(context ->
                        JooqUtil.count(context, SOURCE)));
    }

    /**
     * Get a list of sources that have either been successfully forwarded to all destinations or have been examined and
     * the examined source entries and items have since been deleted, i.e. were no longer needed as aggregate forwarding
     * completed for all entries.
     *
     * @return A list of sources that are ready to be deleted.
     */
    public List<RepoSource> getDeletableSources() {
        return Metrics.measure("SourceDao - getDeletableSources()", () ->
                jooq.readOnlyTransactionResult(context -> context
                                .select(SOURCE.ID,
                                        SOURCE.PATH,
                                        SOURCE.FEED_NAME,
                                        SOURCE.TYPE_NAME,
                                        SOURCE.LAST_MODIFIED_TIME_MS)
                                .from(SOURCE)
                                .where(DELETE_SOURCE_CONDITION)
                                .limit(1000)
                                .fetch())
                        .map(r -> new RepoSource(
                                r.get(SOURCE.ID),
                                r.get(SOURCE.PATH),
                                r.get(SOURCE.FEED_NAME),
                                r.get(SOURCE.TYPE_NAME),
                                r.get(SOURCE.LAST_MODIFIED_TIME_MS)
                        )));
    }

    /**
     * Delete a source record for the provided source id.
     *
     * @param sourceId The id of the source record to delete.
     * @return The number of rows changed.
     */
    public int deleteSource(final long sourceId) {
        return Metrics.measure("SourceDao - deleteSource()", () ->
                jooq.transactionResult(context -> context
                        .deleteFrom(SOURCE)
                        .where(SOURCE.ID.eq(sourceId))
                        .execute()));
    }

    public void resetExamined() {
        Metrics.measure("SourceDao - resetExamined()", () ->
                jooq.transaction(context -> context
                        .update(SOURCE)
                        .set(SOURCE.EXAMINED, false)
                        .execute()));
    }
}
