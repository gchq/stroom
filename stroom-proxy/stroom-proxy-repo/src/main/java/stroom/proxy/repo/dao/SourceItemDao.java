package stroom.proxy.repo.dao;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.SourceItems;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BindWriteQueue;
import stroom.proxy.repo.queue.OperationWriteQueue;
import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.queue.ReadQueue;
import stroom.proxy.repo.queue.RecordQueue;
import stroom.proxy.repo.queue.WriteQueue;
import stroom.util.logging.Metrics;
import stroom.util.shared.Flushable;

import org.jooq.DSLContext;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SourceItemDao implements Flushable {

    private static final Field<?>[] SOURCE_ITEM_COLUMNS = new Field<?>[]{
            SOURCE_ITEM.ID,
            SOURCE_ITEM.NAME,
            SOURCE_ITEM.EXTENSIONS,
            SOURCE_ITEM.FK_FEED_ID,
            SOURCE_ITEM.BYTE_SIZE,
            SOURCE_ITEM.FK_SOURCE_ID,
            SOURCE_ITEM.FILE_STORE_ID,
            SOURCE_ITEM.FK_AGGREGATE_ID,
            SOURCE_ITEM.NEW_POSITION};

    private final SqliteJooqHelper jooq;

    private final SourceDao sourceDao;

    private final AtomicLong sourceItemId = new AtomicLong();
    private final AtomicLong sourceItemNewPosition = new AtomicLong();

    private final RecordQueue recordQueue;
    private final OperationWriteQueue sourceUpdateQueue;
    private final BindWriteQueue sourceItemQueue;
    private final ReadQueue<RepoSourceItemRef> sourceItemReadQueue;

    private final QueueMonitor queueMonitor;

    @Inject
    SourceItemDao(final SqliteJooqHelper jooq,
                  final SourceDao sourceDao,
                  final ProxyDbConfig dbConfig,
                  final QueueMonitors queueMonitors) {
        queueMonitor = queueMonitors.create(3, "Source items");

        this.jooq = jooq;
        this.sourceDao = sourceDao;
        init();

        sourceUpdateQueue = new OperationWriteQueue();
        sourceItemQueue = new BindWriteQueue(SOURCE_ITEM, SOURCE_ITEM_COLUMNS);
        final List<WriteQueue> writeQueues = List.of(sourceItemQueue, sourceUpdateQueue);

        sourceItemReadQueue = new ReadQueue<>(this::read, dbConfig.getBatchSize());
        final List<ReadQueue<?>> readQueues = List.of(sourceItemReadQueue);

        recordQueue = new RecordQueue(jooq, writeQueues, readQueues, dbConfig.getBatchSize());
    }

    private long read(final long currentReadPos, final long limit, List<RepoSourceItemRef> readQueue) {
        queueMonitor.setReadPos(currentReadPos);

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
                    final long newPosition = r.get(SOURCE_ITEM.NEW_POSITION);
                    pos.set(newPosition);
                    queueMonitor.setBufferPos(newPosition);

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

            final long newPosition = JooqUtil
                    .getMaxId(context, SOURCE_ITEM, SOURCE_ITEM.NEW_POSITION)
                    .orElse(0L);
            queueMonitor.setWritePos(newPosition);
            sourceItemNewPosition.set(newPosition);
        });
    }

    public void clear() {
        jooq.transaction(context -> {
            JooqUtil.deleteAll(context, SOURCE_ITEM);
            JooqUtil.checkEmpty(context, SOURCE_ITEM);
        });
        recordQueue.clear();
        init();
    }

    public int countItems() {
        return jooq.readOnlyTransactionResult(context -> JooqUtil.count(context, SOURCE_ITEM));
    }

    public void addItems(final RepoSource source,
                         final Collection<RepoSourceItem> items) {
        recordQueue.add(() -> {
            for (final RepoSourceItem sourceItemRecord : items) {
                final long newPosition = sourceItemNewPosition.incrementAndGet();
                queueMonitor.setWritePos(newPosition);
                final long itemRecordId = sourceItemId.incrementAndGet();

                final Object[] sourceItem = new Object[SOURCE_ITEM_COLUMNS.length];
                sourceItem[0] = itemRecordId;
                sourceItem[1] = sourceItemRecord.name();
                sourceItem[2] = sourceItemRecord.extensions();
                sourceItem[3] = sourceItemRecord.feedId();
                sourceItem[4] = sourceItemRecord.totalByteSize();
                sourceItem[5] = sourceItemRecord.repoSource().id();
                sourceItem[6] = sourceItemRecord.repoSource().fileStoreId();
                sourceItem[7] = sourceItemRecord.aggregateId();
                sourceItem[8] = newPosition;
                sourceItemQueue.add(sourceItem);
            }

            sourceUpdateQueue.add(context ->
                    sourceDao.setSourceExamined(context, source.id(), true, items.size()));
        });
    }

    public Batch<RepoSourceItemRef> getNewSourceItems() {
        return recordQueue.getBatch(sourceItemReadQueue);
    }

    public Batch<RepoSourceItemRef> getNewSourceItems(final long timeout,
                                                      final TimeUnit timeUnit) {
        return recordQueue.getBatch(sourceItemReadQueue, timeout, timeUnit);
    }

    public void deleteBySourceId(final DSLContext context, final long sourceId) {
        // Delete source items.
        Metrics.measure("Delete source items by source id", () -> {
            context
                    .deleteFrom(SOURCE_ITEM)
                    .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId))
                    .execute();
        });
    }

    /**
     * Fetch a list of all source entries that belong to the specified aggregate.
     *
     * @param aggregateId The id of the aggregate to get source entries for.
     * @return A list of source entries for the aggregate.
     */
    public List<SourceItems> fetchSourceItemsByAggregateId(final long aggregateId) {
        final Map<SourceItems.Source, List<SourceItems.Item>> resultMap = new HashMap<>();

        // Get all the source zip entries that we want to write to the forwarding location.
        jooq.readOnlyTransactionResult(context -> context
                        .select(
                                SOURCE_ITEM.ID,
                                SOURCE_ITEM.NAME,
                                SOURCE_ITEM.EXTENSIONS,
                                SOURCE_ITEM.FK_FEED_ID,
                                SOURCE_ITEM.BYTE_SIZE,
                                SOURCE_ITEM.FK_SOURCE_ID,
                                SOURCE_ITEM.FILE_STORE_ID,
                                SOURCE_ITEM.FK_AGGREGATE_ID)
                        .from(SOURCE_ITEM)
                        .where(SOURCE_ITEM.FK_AGGREGATE_ID.eq(aggregateId))
                        .fetch())
                .forEach(r -> {
                    final SourceItems.Source source = new SourceItems.Source(
                            r.get(SOURCE_ITEM.FK_SOURCE_ID),
                            r.get(SOURCE_ITEM.FILE_STORE_ID));

                    final SourceItems.Item item = new SourceItems.Item(
                            r.get(SOURCE_ITEM.ID),
                            r.get(SOURCE_ITEM.NAME),
                            r.get(SOURCE_ITEM.FK_FEED_ID),
                            r.get(SOURCE_ITEM.FK_AGGREGATE_ID),
                            r.get(SOURCE_ITEM.BYTE_SIZE),
                            r.get(SOURCE_ITEM.EXTENSIONS));

                    resultMap.computeIfAbsent(source, s -> new ArrayList<>()).add(item);
                });

        // Sort the sources and items.
        return resultMap
                .entrySet()
                .stream()
                .map(entry -> new SourceItems(entry.getKey(), entry.getValue()))
                .peek(sourceItems -> sourceItems.list().sort(Comparator.comparing(SourceItems.Item::id)))
                .sorted(Comparator.comparing(sourceItems -> sourceItems.source().id()))
                .toList();
    }

    @Override
    public void flush() {
        recordQueue.flush();
    }
}
