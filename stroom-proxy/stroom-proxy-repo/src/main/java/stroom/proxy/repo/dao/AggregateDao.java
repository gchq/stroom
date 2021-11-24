package stroom.proxy.repo.dao;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.WorkQueue;
import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class AggregateDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateDao.class);

    private final SqliteJooqHelper jooq;
    private final AtomicLong aggregateRecordId = new AtomicLong();
    private WorkQueue completedAggregateQueue;

    @Inject
    AggregateDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        completedAggregateQueue = WorkQueue.createWithJooq(jooq, AGGREGATE, AGGREGATE.NEW_POSITION);
        final long maxAggregateRecordId = jooq.getMaxId(AGGREGATE, AGGREGATE.ID).orElse(0L);
        aggregateRecordId.set(maxAggregateRecordId);
    }

    public void clear() {
        jooq.deleteAll(AGGREGATE);
        jooq.checkEmpty(AGGREGATE);
        init();
    }

    /**
     * Close all aggregates that meet the supplied criteria.
     */
    public List<Aggregate> getClosableAggregates(final int maxItemsPerAggregate,
                                                 final long maxUncompressedByteSize,
                                                 final long oldestMs) {
        final Condition condition =
                AGGREGATE.COMPLETE.eq(false)
                        .and(
                                DSL.or(
                                        AGGREGATE.ITEMS.greaterOrEqual(maxItemsPerAggregate),
                                        AGGREGATE.BYTE_SIZE.greaterOrEqual(maxUncompressedByteSize),
                                        AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldestMs)
                                )
                        );

        return jooq.contextResult(context -> context
                        .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                        .from(AGGREGATE)
                        .where(condition)
                        .fetch())
                .map(r -> new Aggregate(
                        r.value1(),
                        r.value2(),
                        r.value3()
                ));
    }

    public void closeAggregate(final Aggregate aggregate) {
        jooq.underLock(() -> {
            completedAggregateQueue.put(writePos ->
                    jooq.context(context -> context
                            .update(AGGREGATE)
                            .set(AGGREGATE.COMPLETE, true)
                            .set(AGGREGATE.NEW_POSITION, writePos.incrementAndGet())
                            .where(AGGREGATE.ID.eq(aggregate.getId()))
                            .execute()));
            return null;
        });
    }

    public Optional<Aggregate> getNewAggregate() {
        return completedAggregateQueue.get(this::getAggregateAtQueuePosition);
    }

    public Optional<Aggregate> getNewAggregate(final long timeout,
                                               final TimeUnit timeUnit) {
        return completedAggregateQueue.get(this::getAggregateAtQueuePosition, timeout, timeUnit);
    }

    private Optional<Aggregate> getAggregateAtQueuePosition(final long position) {
        return jooq.contextResult(context -> context
                        .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                        .from(AGGREGATE)
                        .where(AGGREGATE.NEW_POSITION.eq(position))
                        .orderBy(AGGREGATE.ID)
                        .fetchOptional())
                .map(r -> new Aggregate(
                        r.value1(),
                        r.value2(),
                        r.value3()
                ));
    }

    public void addItem(final RepoSourceItemRef sourceItem,
                        final int maxItemsPerAggregate,
                        final long maxUncompressedByteSize) {
        jooq.underLock(() -> {
            final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - sourceItem.getTotalByteSize());

            LOGGER.debug(() -> "addItem - " +
                    "feed=" +
                    sourceItem.getFeedName() +
                    ", type=" +
                    sourceItem.getTypeName() +
                    ", maxAggregateSize=" +
                    maxAggregateSize +
                    ", maxItemsPerAggregate=" +
                    maxItemsPerAggregate);

            final Condition condition = DSL
                    .and(sourceItem.getFeedName() == null
                            ? AGGREGATE.FEED_NAME.isNull()
                            : AGGREGATE.FEED_NAME.equal(sourceItem.getFeedName()))
                    .and(sourceItem.getTypeName() == null
                            ? AGGREGATE.TYPE_NAME.isNull()
                            : AGGREGATE.TYPE_NAME.equal(sourceItem.getTypeName()))
                    .and(AGGREGATE.BYTE_SIZE.lessOrEqual(maxAggregateSize))
                    .and(AGGREGATE.ITEMS.lessThan(maxItemsPerAggregate))
                    .and(AGGREGATE.COMPLETE.isFalse());

            jooq.transaction(context -> {
                // See if we can get an existing aggregate that will fit this data collection.

                // Note that there may be more than one aggregate that can fit the record as we may have built more than
                // one as a previous one may not have had enough room for a new item so another aggregate might have
                // been started.
                Long aggregateId;
                try (final Cursor<AggregateRecord> cursor = context
                        .selectFrom(AGGREGATE)
                        .where(condition)
                        .orderBy(AGGREGATE.CREATE_TIME_MS)
                        .fetchLazy()) {
                    aggregateId = cursor
                            .fetchNextOptional()
                            .map(AggregateRecord::getId)
                            .orElse(null);
                }

                if (aggregateId != null) {
                    // We have somewhere we can add the data collection so add it to the aggregate.
                    context
                            .update(AGGREGATE)
                            .set(AGGREGATE.BYTE_SIZE, AGGREGATE.BYTE_SIZE.plus(sourceItem.getTotalByteSize()))
                            .set(AGGREGATE.ITEMS, AGGREGATE.ITEMS.plus(1))
                            .where(AGGREGATE.ID.eq(aggregateId))
                            .execute();

                } else {
                    // Create a new aggregate to add this data collection to.
                    aggregateId = aggregateRecordId.incrementAndGet();
                    context
                            .insertInto(
                                    AGGREGATE,
                                    AGGREGATE.ID,
                                    AGGREGATE.FEED_NAME,
                                    AGGREGATE.TYPE_NAME,
                                    AGGREGATE.BYTE_SIZE,
                                    AGGREGATE.ITEMS,
                                    AGGREGATE.CREATE_TIME_MS,
                                    AGGREGATE.COMPLETE)
                            .values(aggregateId,
                                    sourceItem.getFeedName(),
                                    sourceItem.getTypeName(),
                                    sourceItem.getTotalByteSize(),
                                    1,
                                    System.currentTimeMillis(),
                                    false)
                            .execute();
                }

                // Mark the item as added by setting the aggregate id.
                context
                        .update(SOURCE_ITEM)
                        .set(SOURCE_ITEM.AGGREGATE_ID, aggregateId)
                        .setNull(SOURCE_ITEM.NEW_POSITION)
                        .where(SOURCE_ITEM.ID.eq(sourceItem.getId()))
                        .execute();
            });

            return null;
        });
    }

    /**
     * Fetch a list of all source entries that belong to the specified aggregate.
     *
     * @param aggregateId The id of the aggregate to get source entries for.
     * @return A list of source entries for the aggregate.
     */
    public Map<RepoSource, List<RepoSourceItem>> fetchSourceItems(final long aggregateId) {
        final Map<Long, RepoSourceItem> items = new HashMap<>();
        final Map<RepoSource, List<RepoSourceItem>> resultMap = new TreeMap<>(Comparator.comparing(RepoSource::getId));

        // Get all of the source zip entries that we want to write to the forwarding location.
        jooq.contextResult(context -> context
                        .select(
                                SOURCE_ENTRY.ID,
                                SOURCE_ENTRY.EXTENSION,
                                SOURCE_ENTRY.EXTENSION_TYPE,
                                SOURCE_ENTRY.BYTE_SIZE,
                                SOURCE_ITEM.ID,
                                SOURCE_ITEM.SOURCE_ID,
                                SOURCE_ITEM.NAME,
                                SOURCE_ITEM.FEED_NAME,
                                SOURCE_ITEM.TYPE_NAME,
                                SOURCE_ITEM.AGGREGATE_ID,
                                SOURCE.ID,
                                SOURCE.PATH,
                                SOURCE.FEED_NAME,
                                SOURCE.TYPE_NAME,
                                SOURCE.LAST_MODIFIED_TIME_MS,
                                SOURCE.EXAMINED)
                        .from(SOURCE_ENTRY)
                        .join(SOURCE_ITEM).on(SOURCE_ITEM.ID.eq(SOURCE_ENTRY.FK_SOURCE_ITEM_ID))
                        .join(SOURCE).on(SOURCE.ID.eq(SOURCE_ITEM.SOURCE_ID))
                        .where(SOURCE_ITEM.AGGREGATE_ID.eq(aggregateId))
                        .orderBy(SOURCE.ID, SOURCE_ITEM.ID, SOURCE_ENTRY.EXTENSION_TYPE, SOURCE_ENTRY.EXTENSION)
                        .fetch())
                .forEach(r -> {
                    final long id = r.get(SOURCE_ITEM.ID);

                    final RepoSourceItem sourceItem = items.computeIfAbsent(id, k -> {
                        final RepoSource source = RepoSource.builder()
                                .id(r.get(SOURCE.ID))
                                .sourcePath(r.get(SOURCE.PATH))
                                .feedName(r.get(SOURCE.FEED_NAME))
                                .typeName(r.get(SOURCE.TYPE_NAME))
                                .lastModifiedTimeMs(r.get(SOURCE.LAST_MODIFIED_TIME_MS))
                                .build();

                        final RepoSourceItem item = RepoSourceItem.builder()
                                .source(source)
                                .name(r.get(SOURCE_ITEM.NAME))
                                .feedName(r.get(SOURCE_ITEM.FEED_NAME))
                                .typeName(r.get(SOURCE_ITEM.TYPE_NAME))
                                .aggregateId(r.get(SOURCE_ITEM.AGGREGATE_ID))
                                .build();

                        resultMap.computeIfAbsent(source, s -> new ArrayList<>()).add(item);

                        return item;
                    });

                    final RepoSourceEntry entry = RepoSourceEntry.builder()
                            .id(r.get(SOURCE_ENTRY.ID))
                            .type(StroomZipFileType.TYPE_MAP.get(r.get(SOURCE_ENTRY.EXTENSION_TYPE)))
                            .extension(r.get(SOURCE_ENTRY.EXTENSION))
                            .byteSize(r.get(SOURCE_ENTRY.BYTE_SIZE))
                            .build();
                    sourceItem.addEntry(entry);
                });

        return resultMap;
    }

    public int countAggregates() {
        return jooq.count(AGGREGATE);
    }
}
