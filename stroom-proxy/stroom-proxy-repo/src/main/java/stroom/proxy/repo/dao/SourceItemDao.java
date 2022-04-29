package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.RepoDbConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BindWriteQueue;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.proxy.repo.queue.WriteQueue;
import stroom.util.logging.Metrics;
import stroom.util.shared.Flushable;

import org.jooq.DSLContext;
import org.jooq.Field;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SourceItemDao implements Flushable {

    private static final Field<?>[] SOURCE_ITEM_COLUMNS = new Field<?>[]{
            SOURCE_ITEM.ID,
            SOURCE_ITEM.NAME,
            SOURCE_ITEM.FK_FEED_ID,
            SOURCE_ITEM.BYTE_SIZE,
            SOURCE_ITEM.FK_SOURCE_ID,
            SOURCE_ITEM.FK_AGGREGATE_ID,
            SOURCE_ITEM.NEW_POSITION};

    private static final Field<?>[] SOURCE_ENTRY_COLUMNS = new Field<?>[]{
            SOURCE_ENTRY.ID,
            SOURCE_ENTRY.EXTENSION,
            SOURCE_ENTRY.EXTENSION_TYPE,
            SOURCE_ENTRY.BYTE_SIZE,
            SOURCE_ENTRY.FK_SOURCE_ITEM_ID};

    private final SqliteJooqHelper jooq;

    private final SourceDao sourceDao;

    private final AtomicLong sourceItemId = new AtomicLong();
    private final AtomicLong sourceItemNewPosition = new AtomicLong();
    private final AtomicLong sourceEntryId = new AtomicLong();

    private final RecordQueue recordQueue;
    private final OperationWriteQueue sourceUpdateQueue;
    private final BindWriteQueue sourceItemQueue;
    private final BindWriteQueue sourceEntryQueue;
    private final ReadQueue<RepoSourceItemRef> sourceItemReadQueue;

    @Inject
    SourceItemDao(final SqliteJooqHelper jooq,
                  final SourceDao sourceDao,
                  final RepoDbConfig dbConfig) {
        this.jooq = jooq;
        this.sourceDao = sourceDao;
        init();

        sourceUpdateQueue = new OperationWriteQueue();
        sourceItemQueue = new BindWriteQueue(SOURCE_ITEM, SOURCE_ITEM_COLUMNS);
        sourceEntryQueue = new BindWriteQueue(SOURCE_ENTRY, SOURCE_ENTRY_COLUMNS);
        final List<WriteQueue> writeQueues = List.of(sourceItemQueue, sourceEntryQueue, sourceUpdateQueue);

        sourceItemReadQueue = new ReadQueue<>(this::read, dbConfig.getBatchSize());
        final List<ReadQueue<?>> readQueues = List.of(sourceItemReadQueue);

        recordQueue = new RecordQueue(jooq, writeQueues, readQueues, dbConfig.getBatchSize());
    }

    private long read(final long currentReadPos, final long limit, List<RepoSourceItemRef> readQueue) {
        final AtomicLong pos = new AtomicLong(currentReadPos);
        jooq.readOnlyTransactionResult(context -> context
                        .select(SOURCE_ITEM.ID,
                                SOURCE_ITEM.FK_FEED_ID,
                                SOURCE_ITEM.BYTE_SIZE,
                                SOURCE_ITEM.NEW_POSITION)
                        .from(SOURCE_ITEM)
                        .where(SOURCE_ITEM.NEW_POSITION.isNotNull())
                        .and(SOURCE_ITEM.NEW_POSITION.gt(currentReadPos))
                        .orderBy(SOURCE_ITEM.NEW_POSITION)
                        .limit(limit)
                        .fetch())
                .forEach(r -> {
                    pos.set(r.get(SOURCE_ITEM.NEW_POSITION));
                    final RepoSourceItemRef repoSourceItemRef = new RepoSourceItemRef(
                            r.get(SOURCE_ITEM.ID),
                            r.get(SOURCE_ITEM.FK_FEED_ID),
                            r.get(SOURCE_ITEM.BYTE_SIZE)
                    );
                    readQueue.add(repoSourceItemRef);
                });
        return pos.get();
    }

    private void init() {
        jooq.readOnlyTransaction(context -> {
            sourceItemId.set(JooqUtil
                    .getMaxId(context, SOURCE_ITEM, SOURCE_ITEM.ID)
                    .orElse(0L));

            sourceItemNewPosition.set(JooqUtil
                    .getMaxId(context, SOURCE_ITEM, SOURCE_ITEM.NEW_POSITION)
                    .orElse(0L));

            sourceEntryId.set(JooqUtil
                    .getMaxId(context, SOURCE_ENTRY, SOURCE_ENTRY.ID)
                    .orElse(0L));
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, SOURCE_ENTRY);
            JooqUtil.deleteAll(context, SOURCE_ITEM);
            JooqUtil.checkEmpty(context, SOURCE_ENTRY);
            JooqUtil.checkEmpty(context, SOURCE_ITEM);
        });
        recordQueue.clear();
        init();
    }

    public int countItems() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, SOURCE_ITEM));
    }

    public int countEntries() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, SOURCE_ENTRY));
    }

    public void addItems(final RepoSource source,
                         final Collection<RepoSourceItem> items) {
        recordQueue.add(() -> {
            for (final RepoSourceItem sourceItemRecord : items) {
                final long itemRecordId = sourceItemId.incrementAndGet();

                final Object[] sourceItem = new Object[SOURCE_ITEM_COLUMNS.length];
                sourceItem[0] = itemRecordId;
                sourceItem[1] = sourceItemRecord.getName();
                sourceItem[2] = sourceItemRecord.getFeedId();
                sourceItem[3] = sourceItemRecord.getTotalByteSize();
                sourceItem[4] = sourceItemRecord.getSource().id();
                sourceItem[5] = sourceItemRecord.getAggregateId();
                sourceItem[6] = sourceItemNewPosition.incrementAndGet();
                sourceItemQueue.add(sourceItem);

                final List<RepoSourceEntry> entries = sourceItemRecord.getEntries();
                for (final RepoSourceEntry entry : entries) {
                    final long entryRecordId = sourceEntryId.incrementAndGet();

                    final Object[] sourceEntry = new Object[SOURCE_ENTRY_COLUMNS.length];
                    sourceEntry[0] = entryRecordId;
                    sourceEntry[1] = entry.extension();
                    sourceEntry[2] = entry.type().getId();
                    sourceEntry[3] = entry.byteSize();
                    sourceEntry[4] = itemRecordId;
                    sourceEntryQueue.add(sourceEntry);
                }
            }

            sourceUpdateQueue.add(context -> sourceDao.setSourceExamined(context, source.id()));
        });
    }

    public Batch<RepoSourceItemRef> getNewSourceItems() {
        return recordQueue.getBatch(sourceItemReadQueue);
    }

    public Batch<RepoSourceItemRef> getNewSourceItems(final long timeout,
                                                      final TimeUnit timeUnit) {
        return recordQueue.getBatch(sourceItemReadQueue, timeout, timeUnit);
    }

    public void deleteByAggregateId(final DSLContext context, final long aggregateId) {
        Metrics.measure("Delete source entries", () -> {
            context
                    .deleteFrom(SOURCE_ENTRY)
                    .where(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.in(
                            context
                                    .select(SOURCE_ITEM.ID)
                                    .from(SOURCE_ITEM)
                                    .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId)))
                    )
                    .execute();
        });

        // Delete source items.
        Metrics.measure("Delete source items", () -> {
            context
                    .deleteFrom(SOURCE_ITEM)
                    .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId))
                    .execute();
        });
    }

    public void deleteBySourceId(final DSLContext context, final long sourceId) {
        Metrics.measure("Delete source entries", () -> {
            context
                    .deleteFrom(SOURCE_ENTRY)
                    .where(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.in(
                            context
                                    .select(SOURCE_ITEM.ID)
                                    .from(SOURCE_ITEM)
                                    .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId)))
                    )
                    .execute();
        });

        // Delete source items.
        Metrics.measure("Delete source items", () -> {
            context
                    .deleteFrom(SOURCE_ITEM)
                    .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId))
                    .execute();
        });
    }

    @Override
    public void flush() {
        recordQueue.flush();
    }
}
