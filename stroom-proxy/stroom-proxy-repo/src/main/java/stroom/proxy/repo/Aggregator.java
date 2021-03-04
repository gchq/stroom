/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.repo;

import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;

import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class Aggregator {

    private static final int BATCH_SIZE = 1000000;

    private final SqliteJooqHelper jooq;
    private final AggregatorConfig config;
    private long lastClosedAggregates;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicLong aggregateRecordId = new AtomicLong();

    private volatile boolean firstRun = true;

    @Inject
    Aggregator(final ProxyRepoDbConnProvider connProvider,
               final AggregatorConfig config) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.config = config;

        final long maxAggregateRecordId = jooq.contextResult(context -> context
                .select(DSL.max(AGGREGATE.ID))
                .from(AGGREGATE)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0L));
        aggregateRecordId.set(maxAggregateRecordId);
    }

    public synchronized void aggregate() {
        // Start by trying to close old aggregates.
        closeOldAggregates();

        boolean run = true;
        while (run) {

            final AtomicInteger count = new AtomicInteger();

            final Result<Record4<Long, String, String, BigDecimal>> result = jooq.contextResult(context -> context
                    // Get all data items that have not been added to aggregate destinations.
                    .select(SOURCE_ITEM.ID,
                            SOURCE_ITEM.FEED_NAME,
                            SOURCE_ITEM.TYPE_NAME,
                            DSL.sum(SOURCE_ENTRY.BYTE_SIZE))
                    .from(SOURCE_ITEM)
                    .join(SOURCE).on(SOURCE.ID.eq(SOURCE_ITEM.FK_SOURCE_ID))
                    .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .where(SOURCE_ITEM.AGGREGATED.isFalse())
                    .groupBy(SOURCE_ITEM.ID)
                    .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID, SOURCE_ITEM.NUMBER)
                    .limit(BATCH_SIZE)
                    .fetch());
            result.forEach(record -> {
                final long sourceItemId = record.value1();
                final String feedName = record.value2();
                final String typeName = record.value3();
                final BigDecimal totalSize = record.value4();
                addItem(sourceItemId, feedName, typeName, totalSize.longValue());

                count.incrementAndGet();
            });

            // Stop aggregating if the last query did not return a result as big as the batch size.
            if (count.get() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    public synchronized void aggregate(final long sourceId) {
        // Start by trying to close old aggregates.
        closeOldAggregates();

        final Result<Record4<Long, String, String, BigDecimal>> result = jooq.contextResult(context -> context
                // Get all data items that have not been added to aggregate destinations.
                .select(SOURCE_ITEM.ID,
                        SOURCE_ITEM.FEED_NAME,
                        SOURCE_ITEM.TYPE_NAME,
                        DSL.sum(SOURCE_ENTRY.BYTE_SIZE))
                .from(SOURCE_ITEM)
                .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId))
                .and(SOURCE_ITEM.AGGREGATED.isFalse())
                .groupBy(SOURCE_ITEM.ID)
                .orderBy(SOURCE_ITEM.NUMBER)
                .fetch());
        result.forEach(record -> {
            final long sourceItemId = record.value1();
            final String feedName = record.value2();
            final String typeName = record.value3();
            final BigDecimal totalSize = record.value4();

            addItem(sourceItemId, feedName, typeName, totalSize.longValue());
        });
    }

    private synchronized void addItem(final long sourceItemId,
                                      final String feedName,
                                      final String typeName,
                                      final long totalSize) {
        final long maxUncompressedByteSize = config.getMaxUncompressedByteSize();
        final int maxItemsPerAggregate = config.getMaxItemsPerAggregate();
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - totalSize);

        jooq.transaction(context -> {
            // See if we can get an existing aggregate that will fit this data collection.
            final Optional<AggregateRecord> optionalRecord = context
                    .selectFrom(AGGREGATE)
                    .where(AGGREGATE.FEED_NAME.equal(feedName))
                    .and(AGGREGATE.TYPE_NAME.equal(typeName))
                    .and(AGGREGATE.BYTE_SIZE.lessOrEqual(maxAggregateSize))
                    .and(AGGREGATE.ITEMS.lessThan(maxItemsPerAggregate))
                    .and(AGGREGATE.COMPLETE.isFalse())
                    .orderBy(AGGREGATE.CREATE_TIME_MS)
                    .fetchOptional();

            long aggregateId;
            if (optionalRecord.isPresent()) {
                aggregateId = optionalRecord.get().getId();

                // We have somewhere we can add the data collection so add it to the aggregate.
                context
                        .update(AGGREGATE)
                        .set(AGGREGATE.BYTE_SIZE, AGGREGATE.BYTE_SIZE.plus(totalSize))
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
                                AGGREGATE.CREATE_TIME_MS)
                        .values(aggregateId,
                                feedName,
                                typeName,
                                totalSize,
                                1,
                                System.currentTimeMillis())
                        .execute();
            }

            // Add the item.
            context
                    .insertInto(AGGREGATE_ITEM, AGGREGATE_ITEM.FK_AGGREGATE_ID, AGGREGATE_ITEM.FK_SOURCE_ITEM_ID)
                    .values(aggregateId, sourceItemId)
                    .execute();

            // Mark the item as added.
            context
                    .update(SOURCE_ITEM)
                    .set(SOURCE_ITEM.AGGREGATED, true)
                    .where(SOURCE_ITEM.ID.eq(sourceItemId))
                    .execute();
        });

        // Close any old aggregates.
        closeOldAggregates();
    }

    synchronized void closeOldAggregates() {
        final long now = System.currentTimeMillis();
        if (now > lastClosedAggregates + config.getAggregationFrequency().toMillis()) {
            lastClosedAggregates = now;

            final long oldest = now - config.getMaxAggregateAge().toMillis();
            closeOldAggregates(oldest);
        }
    }

    synchronized void closeOldAggregates(final long oldest) {
        final Condition condition =
                AGGREGATE.COMPLETE.eq(false)
                        .and(
                                DSL.or(
                                        AGGREGATE.ITEMS.greaterOrEqual(config.getMaxItemsPerAggregate()),
                                        AGGREGATE.BYTE_SIZE.greaterOrEqual(config.getMaxUncompressedByteSize()),
                                        AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldest)
                                )
                        );

        final int count = closeAggregates(condition);

        // If we have closed some aggregates then let others know there are some available.
        if (count > 0 || firstRun) {
            firstRun = false;
            fireChange(count);
        }
    }

    private synchronized int closeAggregates(final Condition condition) {
        return jooq.contextResult(context -> context
                .update(AGGREGATE)
                .set(AGGREGATE.COMPLETE, true)
                .where(condition)
                .execute());
    }

    private void fireChange(final int count) {
        listeners.forEach(listener -> listener.onChange(count));
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(int count);
    }
}
