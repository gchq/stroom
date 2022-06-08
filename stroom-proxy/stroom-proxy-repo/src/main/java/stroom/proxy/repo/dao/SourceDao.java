package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BindWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.proxy.repo.queue.WriteQueue;
import stroom.util.shared.Flushable;

import org.jooq.DSLContext;
import org.jooq.Field;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@Singleton
public class SourceDao implements Flushable {

    private static final Field<?>[] SOURCE_COLUMNS = new Field<?>[]{
            SOURCE.ID,
            SOURCE.FILE_STORE_ID,
            SOURCE.FK_FEED_ID,
            SOURCE.NEW_POSITION};

    private final SqliteJooqHelper jooq;
    private final FeedDao feedDao;

    private final AtomicLong sourceId = new AtomicLong();
    private final AtomicLong sourceNewPosition = new AtomicLong();

    private final RecordQueue recordQueue;
    private final BindWriteQueue sourceQueue;
    private final ReadQueue<RepoSource> sourceReadQueue;


    @Inject
    SourceDao(final SqliteJooqHelper jooq,
              final FeedDao feedDao,
              final ProxyDbConfig dbConfig) {
        this.jooq = jooq;
        this.feedDao = feedDao;
        init();

        sourceQueue = new BindWriteQueue(SOURCE, SOURCE_COLUMNS);
        final List<WriteQueue> writeQueues = List.of(sourceQueue);

        sourceReadQueue = new ReadQueue<>(this::read, dbConfig.getBatchSize());
        final List<ReadQueue<?>> readQueues = List.of(sourceReadQueue);

        recordQueue = new RecordQueue(jooq, writeQueues, readQueues, dbConfig.getBatchSize());
    }

    private long read(final long currentReadPos, final long limit, List<RepoSource> readQueue) {
        final AtomicLong pos = new AtomicLong(currentReadPos);
        jooq.readOnlyTransactionResult(context -> context
                        .select(SOURCE.ID,
                                SOURCE.FILE_STORE_ID,
                                SOURCE.FK_FEED_ID,
                                SOURCE.NEW_POSITION)
                        .from(SOURCE)
                        .where(SOURCE.NEW_POSITION.isNotNull())
                        .and(SOURCE.NEW_POSITION.gt(currentReadPos))
                        .orderBy(SOURCE.NEW_POSITION)
                        .limit(limit)
                        .fetch())
                .forEach(r -> {
                    pos.set(r.get(SOURCE.NEW_POSITION));
                    final RepoSource repoSource = new RepoSource(
                            r.get(SOURCE.ID),
                            r.get(SOURCE.FILE_STORE_ID),
                            r.get(SOURCE.FK_FEED_ID));
                    readQueue.add(repoSource);
                });
        return pos.get();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            sourceId.set(JooqUtil
                    .getMaxId(context, SOURCE, SOURCE.ID)
                    .orElse(0L));
            sourceNewPosition.set(JooqUtil
                    .getMaxId(context, SOURCE, SOURCE.NEW_POSITION)
                    .orElse(0L));
        });
    }

//    public long getMaxId() {
//        return jooq.readOnlyTransactionResult(context ->
//                JooqUtil.getMaxId(context, SOURCE, SOURCE.ID).orElse(0L));
//    }

    public long getMaxFileStoreId() {
        return jooq.readOnlyTransactionResult(context ->
                JooqUtil.getMaxId(context, SOURCE, SOURCE.FILE_STORE_ID).orElse(0L));
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, SOURCE);
            JooqUtil.checkEmpty(context, SOURCE);
        });
        recordQueue.clear();
        init();
    }

//    public boolean pathExists(final String path) {
//        return jooq.readOnlyTransactionResult(context -> context
//                .fetchExists(
//                        context
//                                .selectFrom(SOURCE)
//                                .where(SOURCE.PATH.eq(path))
//                ));
//    }

    /**
     * Add a new source to the database.
     * <p>
     * If a new source is successfully added then return it in the optional result. If a source for the supplied path
     * already exists then return an empty optional.
     * <p>
     * This method is synchronized to cope with sources being added via receipt and repo scanning at the same time.
     *
     * @param fileStoreId The file store id of the source to add.
     * @param feedName    The feed name associated with the source.
     * @param typeName    The type name associated with the source.
     */
    public void addSource(final long fileStoreId,
                          final String feedName,
                          final String typeName) {
        final long feedId = feedDao.getId(new FeedKey(feedName, typeName));
        recordQueue.add(() -> {
            final Object[] source = new Object[SOURCE_COLUMNS.length];
            source[0] = sourceId.incrementAndGet();
            source[1] = fileStoreId;
            source[2] = feedId;
            source[3] = sourceNewPosition.incrementAndGet();
            sourceQueue.add(source);
        });
    }

    public Batch<RepoSource> getNewSources() {
        return recordQueue.getBatch(sourceReadQueue);
    }

    public Batch<RepoSource> getNewSources(final long timeout,
                                           final TimeUnit timeUnit) {
        return recordQueue.getBatch(sourceReadQueue, timeout, timeUnit);
    }

    public int countSources() {
        return jooq.readOnlyTransactionResult(context ->
                JooqUtil.count(context, SOURCE));
    }

    /**
     * Mark sources as being ready for deletion.
     */
    public void markDeletableSources() {
        jooq.transaction(context -> context
                .update(SOURCE)
                .set(SOURCE.DELETED, true)
                .where(SOURCE.EXAMINED.isTrue())
                .and(SOURCE.ITEM_COUNT.eq(0))
                .execute());
    }

    /**
     * Get a list of sources that have either been successfully forwarded to all destinations or have been examined and
     * the examined source entries and items have since been deleted, i.e. were no longer needed as aggregate forwarding
     * completed for all entries.
     *
     * @return A list of sources that are ready to be deleted.
     */
    public List<RepoSource> getDeletableSources(final long minSourceId,
                                                final int limit) {
        return jooq.readOnlyTransactionResult(context -> context
                        .select(SOURCE.ID,
                                SOURCE.FILE_STORE_ID,
                                SOURCE.FK_FEED_ID)
                        .from(SOURCE)
                        .where(SOURCE.DELETED.isTrue())
                        .and(SOURCE.ID.gt(minSourceId))
                        .orderBy(SOURCE.ID)
                        .limit(limit)
                        .fetch())
                .map(r -> new RepoSource(
                        r.get(SOURCE.ID),
                        r.get(SOURCE.FILE_STORE_ID),
                        r.get(SOURCE.FK_FEED_ID)));
    }

    /**
     * Used for testing.
     *
     * @return
     */
    public int countDeletableSources() {
        markDeletableSources();
        return getDeletableSources(0, 1000).size();
    }

    /**
     * Delete sources that have already been marked for deletion.
     *
     * @return The number of rows changed.
     */
    public int deleteSources() {
        return jooq.transactionResult(context -> context
                .deleteFrom(SOURCE)
                .where(SOURCE.DELETED.isTrue())
                .execute());
    }

    public void resetExamined() {
        jooq.transaction(context -> context
                .update(SOURCE)
                .set(SOURCE.EXAMINED, false)
                .set(SOURCE.ITEM_COUNT, 0)
                .execute());
    }

    public void setSourceExamined(final long sourceId,
                                  final boolean examined,
                                  final int itemCount) {
        jooq.transaction(context -> setSourceExamined(context, sourceId, examined, itemCount));
    }

    public void setSourceExamined(final DSLContext context,
                                  final long sourceId,
                                  final boolean examined,
                                  final int itemCount) {
        context
                .update(SOURCE)
                .set(SOURCE.EXAMINED, examined)
                .set(SOURCE.ITEM_COUNT, itemCount)
                .setNull(SOURCE.NEW_POSITION)
                .where(SOURCE.ID.eq(sourceId))
                .execute();
    }

    @Override
    public void flush() {
        recordQueue.flush();
    }

    public void clearQueue() {
        recordQueue.clear();
    }
}
