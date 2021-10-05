package stroom.proxy.repo.dao;

import stroom.db.util.SqliteJooqHelper;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.proxy.repo.dao.SourceEntryDao.SourceItem;
import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;


@Singleton
public class AggregateDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateDao.class);

    private final SqliteJooqHelper jooq;
    private final AtomicLong aggregateRecordId = new AtomicLong();
    private final AtomicLong aggregateItemRecordId = new AtomicLong();

    @Inject
    AggregateDao(final ProxyRepoDbConnProvider connProvider) {
        this.jooq = new SqliteJooqHelper(connProvider);
        init();
    }

    private void init() {
        final long maxAggregateRecordId = jooq.getMaxId(AGGREGATE, AGGREGATE.ID).orElse(0L);
        aggregateRecordId.set(maxAggregateRecordId);
        final long maxAggregateItemRecordId = jooq.getMaxId(AGGREGATE_ITEM, AGGREGATE_ITEM.ID).orElse(0L);
        aggregateItemRecordId.set(maxAggregateItemRecordId);
    }

    /**
     * Resets any forward failure flags that are used to tell the aggregate forwarder not to endlessly send the same
     * aggregate.
     *
     * @return The number of rows changed.
     */
    public synchronized int resetFailedForwards() {
        return jooq.contextResult(context -> context
                .update(AGGREGATE)
                .set(AGGREGATE.FORWARD_ERROR, false)
                .execute());
    }

    /**
     * Marks an aggregate as having errors so we don't keep endlessly trying to send it.
     *
     * @param aggregateId The id of the aggregate to record a forwarding error against.
     */
    public synchronized void setForwardError(final long aggregateId) {
        jooq.context(context -> context
                .update(AGGREGATE)
                .set(AGGREGATE.FORWARD_ERROR, true)
                .where(AGGREGATE.ID.eq(aggregateId))
                .execute());
    }

    public synchronized void setForwardSuccess(final DSLContext context, final long aggregateId) {
        // Delete aggregate items and aggregate so that source entries and items can be deleted by cleanup.
        context
                .deleteFrom(AGGREGATE_ITEM)
                .where(AGGREGATE_ITEM.FK_AGGREGATE_ID.equal(aggregateId))
                .execute();

        context
                .deleteFrom(AGGREGATE)
                .where(AGGREGATE.ID.equal(aggregateId))
                .execute();
    }

    /**
     * Close all aggregates that meet the supplied criteria.
     */
    public synchronized int closeAggregates(final int maxItemsPerAggregate,
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
                .update(AGGREGATE)
                .set(AGGREGATE.COMPLETE, true)
                .where(condition)
                .execute());
    }

    /**
     * Gets a list of all aggregates that are ready to forward and don't already have forwarding errors associated with
     * them.
     *
     * @param limit The maximum number of aggregates to return.
     * @return A list of aggregates that are ready and waiting to be forwarded.
     */
    public synchronized List<Aggregate> getCompletedAggregates(final int limit) {
        return jooq.contextResult(context -> context
                // Get all completed aggregates.
                .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                .from(AGGREGATE)
                .where(AGGREGATE.COMPLETE.isTrue())
                .and(AGGREGATE.FORWARD_ERROR.isFalse())
                .orderBy(AGGREGATE.CREATE_TIME_MS)
                .limit(limit)
                .fetch()
                .map(r -> new Aggregate(
                        r.value1(),
                        r.value2(),
                        r.value3()
                )));
    }

    public synchronized void addItem(final SourceItem sourceItem,
                                     final int maxItemsPerAggregate,
                                     final long maxUncompressedByteSize) {
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - sourceItem.getByteSize());

        LOGGER.debug(() -> "addItem - " +
                ", feed=" +
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
            final Result<AggregateRecord> result = context
                    .selectFrom(AGGREGATE)
                    .where(condition)
                    .orderBy(AGGREGATE.CREATE_TIME_MS)
                    .fetch();

            AggregateRecord record = null;
            if (result.size() > 0) {
                record = result.get(0);
                if (result.size() > 1) {
                    LOGGER.warn(() -> "Received more that one result for aggregate query " + condition + "\n" + result);
                }
            }

            long aggregateId;
            if (record != null) {
                aggregateId = record.getId();

                // We have somewhere we can add the data collection so add it to the aggregate.
                context
                        .update(AGGREGATE)
                        .set(AGGREGATE.BYTE_SIZE, AGGREGATE.BYTE_SIZE.plus(sourceItem.getByteSize()))
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
                                sourceItem.getByteSize(),
                                1,
                                System.currentTimeMillis(),
                                false)
                        .execute();
            }

            // Add the item.
            context
                    .insertInto(
                            AGGREGATE_ITEM,
                            AGGREGATE_ITEM.ID,
                            AGGREGATE_ITEM.FK_AGGREGATE_ID,
                            AGGREGATE_ITEM.FK_SOURCE_ITEM_ID)
                    .values(aggregateItemRecordId.incrementAndGet(), aggregateId, sourceItem.getSourceItemId())
                    .execute();

            // Mark the item as added.
            context
                    .update(SOURCE_ITEM)
                    .set(SOURCE_ITEM.AGGREGATED, true)
                    .where(SOURCE_ITEM.ID.eq(sourceItem.getSourceItemId()))
                    .execute();
        });
    }

    public synchronized int deleteAll() {
        return jooq.contextResult(context -> {
            int total = 0;
            total += context
                    .deleteFrom(AGGREGATE_ITEM)
                    .execute();
            total += context
                    .deleteFrom(AGGREGATE)
                    .execute();
            return total;
        });
    }

    public synchronized void clear() {
        deleteAll();
        jooq
                .getMaxId(AGGREGATE_ITEM, AGGREGATE_ITEM.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        jooq
                .getMaxId(AGGREGATE, AGGREGATE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        init();
    }

    /**
     * Fetch a list of all source entries that belong to the specified aggregate.
     *
     * @param aggregateId The id of the aggregate to get source entries for.
     * @return A list of source entries for the aggregate.
     */
    public List<SourceEntry> fetchSourceEntries(final long aggregateId) {
        // Get all of the source zip entries that we want to write to the forwarding location.
        return jooq.contextResult(context -> context
                .select(SOURCE.PATH,
                        SOURCE_ITEM.NAME,
                        SOURCE_ENTRY.EXTENSION)
                .from(SOURCE)
                .join(SOURCE_ITEM).on(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))
                .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                .join(AGGREGATE_ITEM).on(AGGREGATE_ITEM.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                .join(AGGREGATE).on(AGGREGATE.ID.eq(AGGREGATE_ITEM.FK_AGGREGATE_ID))
                .where(AGGREGATE.ID.eq(aggregateId))
                .orderBy(SOURCE.ID, SOURCE_ITEM.ID, SOURCE_ENTRY.EXTENSION_TYPE, SOURCE_ENTRY.EXTENSION)
                .fetch()
                .map(r -> new SourceEntry(
                        r.value1(),
                        r.value2(),
                        r.value3()
                )));
    }

    public static class Aggregate {

        private final long aggregateId;
        private final String feedName;
        private final String typeName;

        public Aggregate(final long aggregateId,
                         final String feedName,
                         final String typeName) {
            this.aggregateId = aggregateId;
            this.feedName = feedName;
            this.typeName = typeName;
        }

        public long getAggregateId() {
            return aggregateId;
        }

        public String getFeedName() {
            return feedName;
        }

        public String getTypeName() {
            return typeName;
        }
    }

    public static class SourceEntry {

        private final String sourcePath;
        private final String name;
        private final String extension;

        public SourceEntry(final String sourcePath,
                           final String name,
                           final String extension) {
            this.sourcePath = sourcePath;
            this.name = name;
            this.extension = extension;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public String getName() {
            return name;
        }

        public String getExtension() {
            return extension;
        }
    }
}
