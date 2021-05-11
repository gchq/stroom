package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoDbConnProvider;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
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
                                    .where(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))));

    private final SqliteJooqHelper jooq;

    private final AtomicLong sourceRecordId = new AtomicLong();

    @Inject
    SourceDao(final ProxyRepoDbConnProvider connProvider) {
        this.jooq = new SqliteJooqHelper(connProvider);
        init();
    }

    private void init() {
        final long maxSourceRecordId = jooq.getMaxId(SOURCE, SOURCE.ID).orElse(0L);
        sourceRecordId.set(maxSourceRecordId);
    }

    public Optional<Long> getSourceId(final String path) {
        return jooq.contextResult(context -> context
                .select(SOURCE.ID)
                .from(SOURCE)
                .where(SOURCE.PATH.eq(path))
                .fetchOptional(SOURCE.ID));
    }

    public Source addSource(final String path,
                          final String feedName,
                          final String typeName,
                          final long lastModifiedTimeMs) {
        final long sourceId = sourceRecordId.incrementAndGet();
        jooq.context(context -> context
                .insertInto(
                        SOURCE,
                        SOURCE.ID,
                        SOURCE.PATH,
                        SOURCE.FEED_NAME,
                        SOURCE.TYPE_NAME,
                        SOURCE.LAST_MODIFIED_TIME_MS
                )
                .values(
                        sourceId,
                        path,
                        feedName,
                        typeName,
                        lastModifiedTimeMs
                )
                .execute());
        return new Source(sourceId, path, feedName, typeName);
    }

    /**
     * Set all sources to no longer be examined as we are no longer going to aggregate data.
     *
     * @return The number of rows changed.
     */
    public int resetExamined() {
        return jooq.contextResult(context -> context
                .update(SOURCE)
                .set(SOURCE.EXAMINED, false)
                .execute());
    }

    /**
     * Resets any forward failure flags that are used to tell the source forwarder not to endlessly send the same data.
     *
     * @return The number of rows changed.
     */
    public int resetFailedForwards() {
        return jooq.contextResult(context -> context
                .update(SOURCE)
                .set(SOURCE.FORWARD_ERROR, false)
                .execute());
    }

    /**
     * Marks a source as having errors so we don't keep endlessly trying to send it.
     *
     * @param sourceId The id of the source to record a forwarding error against.
     */
    public void setForwardError(final long sourceId) {
        jooq.context(context -> context
                .update(SOURCE)
                .set(SOURCE.FORWARD_ERROR, true)
                .where(SOURCE.ID.eq(sourceId))
                .execute());
    }

    public void setForwardSuccess(final long sourceId) {
        jooq.context(context -> setForwardSuccess(context, sourceId));
    }

    public void setForwardSuccess(final DSLContext context, final long sourceId) {
        // Record the source as forwarded so it can be deleted by Cleanup.
        context
                .update(SOURCE)
                .set(SOURCE.FORWARDED, true)
                .where(SOURCE.ID.eq(sourceId))
                .execute();
    }

    /**
     * Gets a list of all sources that are ready to be examined and don't already marked as examined.
     *
     * @param limit The maximum number of sources to return.
     * @return A list of sources that are ready and waiting to be examined.
     */
    public List<Source> getNewSources(final int limit) {
        return jooq.contextResult(context -> context
                .select(SOURCE.ID, SOURCE.PATH, SOURCE.FEED_NAME, SOURCE.TYPE_NAME)
                .from(SOURCE)
                .where(SOURCE.EXAMINED.isFalse())
                .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID)
                .limit(limit)
                .fetch()
                .map(r -> new Source(
                        r.value1(),
                        r.value2(),
                        r.value3(),
                        r.value4()
                )));
    }

    public int countSources() {
        return jooq.count(SOURCE);
    }

    /**
     * Gets a list of all sources that are ready to forward and don't already have forwarding errors associated with
     * them.
     *
     * @param limit The maximum number of sources to return.
     * @return A list of sources that are ready and waiting to be forwarded.
     */
    public List<Source> getCompletedSources(final int limit) {
        return jooq.contextResult(context -> context
                // Get all completed sources.
                .select(SOURCE.ID, SOURCE.PATH, SOURCE.FEED_NAME, SOURCE.TYPE_NAME)
                .from(SOURCE)
                .where(SOURCE.FORWARD_ERROR.isFalse())
                .orderBy(SOURCE.LAST_MODIFIED_TIME_MS)
                .limit(limit)
                .fetch()
                .map(r -> new Source(
                        r.value1(),
                        r.value2(),
                        r.value3(),
                        r.value4()
                )));
    }

    /**
     * Get a list of sources that have either been successfully forwarded to all destinations or have been examined and
     * the examined source entries and items have since been deleted, i.e. were no longer needed as aggregate forwarding
     * completed for all entries.
     *
     * @param limit The maximum number of sources to return.
     * @return A list of sources that are ready to be deleted.
     */
    public List<Source> getDeletableSources(final int limit) {
        return jooq.contextResult(context -> context
                .selectDistinct(SOURCE.ID, SOURCE.PATH)
                .from(SOURCE)
                .where(DELETE_SOURCE_CONDITION)
                .limit(limit)
                .fetch()
                .map(r -> new Source(
                        r.value1(),
                        r.value2(),
                        null,
                        null
                )));
    }

    /**
     * Delete a source record for the provided source id.
     *
     * @param sourceId The id of the source record to delete.
     * @return The number of rows changed.
     */
    public int deleteSource(final long sourceId) {
        return jooq.contextResult(context -> context
                .deleteFrom(SOURCE)
                .where(SOURCE.ID.eq(sourceId))
                .execute());
    }

    public void deleteAll() {
        jooq.deleteAll(SOURCE);
    }

    public void clear() {
        deleteAll();
        jooq
                .getMaxId(SOURCE, SOURCE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        init();
    }

    public static class Source {

        private final long sourceId;
        private final String sourcePath;
        private final String feedName;
        private final String typeName;

        public Source(final long sourceId,
                      final String sourcePath,
                      final String feedName,
                      final String typeName) {
            this.sourceId = sourceId;
            this.sourcePath = sourcePath;
            this.feedName = feedName;
            this.typeName = typeName;
        }

        public long getSourceId() {
            return sourceId;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public String getFeedName() {
            return feedName;
        }

        public String getTypeName() {
            return typeName;
        }
    }
}
