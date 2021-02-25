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

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.db.jooq.tables.records.AggregateRecord;

import org.jooq.Condition;
import org.jooq.Record4;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

class Aggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Aggregator.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final AggregatorConfig config;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private AggregateDataCollection currentCollection;
    private long lastClosedAggregates = -1;

    @Inject
    Aggregator(final ProxyRepoDbConnProvider connProvider,
               final AggregatorConfig config) {
        this.connProvider = connProvider;
        this.config = config;

        // Start aggregation.
        aggregate();
    }

    public void aggregate() {
        final long start = System.currentTimeMillis();

        // Start by trying to close old aggregates.
        closeOldAggregates(start);

        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all data items that have not been added to aggregate destinations.
            try (final Stream<Record4<Integer, String, String, Long>> stream = context
                    .select(SOURCE_ITEM.ID,
                            SOURCE_ITEM.FEED_NAME,
                            SOURCE_ITEM.TYPE_NAME,
                            SOURCE_ENTRY.BYTE_SIZE)
                    .from(SOURCE_ITEM)
                    .join(SOURCE).on(SOURCE.ID.eq(SOURCE_ITEM.FK_SOURCE_ID))
                    .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .and(SOURCE_ITEM.IN_AGGREGATE.isFalse())
                    .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID, SOURCE_ITEM.NUMBER)
                    .stream()) {
                stream.forEach(record -> {
                    final int dataId = record.get(SOURCE_ITEM.ID);
                    final String feedName = record.get(SOURCE_ITEM.FEED_NAME);
                    final String typeName = record.get(SOURCE_ITEM.TYPE_NAME);
                    final long byteSize = record.get(SOURCE_ENTRY.BYTE_SIZE);

                    if (currentCollection != null && currentCollection.dataId != dataId) {
                        addCollection(currentCollection);
                        currentCollection = null;
                        closeOldAggregates(System.currentTimeMillis());
                    }

                    if (currentCollection == null) {
                        currentCollection = new AggregateDataCollection(
                                dataId,
                                feedName,
                                typeName);
                    }
                    currentCollection.addEntry(byteSize);
                });
            }
        });

        // Add final entry.
        if (currentCollection != null) {
            addCollection(currentCollection);
            currentCollection = null;
            closeOldAggregates(System.currentTimeMillis());
        }

        // Run the aggregation again on the next set of data.
        final long frequency = config.getAggregationFrequency().toMillis();
        // How long were we running for?
        final long duration = System.currentTimeMillis() - start;
        final long delay = Math.max(0, frequency - duration);
        executorService.schedule(this::aggregate, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void addCollection(final AggregateDataCollection collection) {
        final long maxUncompressedByteSize = config.getMaxUncompressedByteSize();
        final int maxItemsPerAggregate = config.getMaxItemsPerAggregate();
        final long maxDestSize = Math.max(0, maxUncompressedByteSize - collection.totalSize);

        JooqUtil.transaction(connProvider, context -> {
            // See if we can get an existing aggregate that will fit this data collection.
            final Optional<AggregateRecord> optionalRecord = context
                    .selectFrom(AGGREGATE)
                    .where(AGGREGATE.FEED_NAME.equal(collection.feedName))
                    .and(AGGREGATE.TYPE_NAME.equal(collection.typeName))
                    .and(AGGREGATE.BYTE_SIZE.lessOrEqual(maxDestSize))
                    .and(AGGREGATE.ITEMS.lessThan(maxItemsPerAggregate))
                    .and(AGGREGATE.COMPLETE.isFalse())
                    .orderBy(AGGREGATE.CREATE_TIME_MS)
                    .fetchOptional();

            int destId;
            if (optionalRecord.isPresent()) {
                destId = optionalRecord.get().getId();

                // We have somewhere we can add the data collection so add it to the aggregate.
                context
                        .update(AGGREGATE)
                        .set(AGGREGATE.BYTE_SIZE, AGGREGATE.BYTE_SIZE.plus(collection.totalSize))
                        .set(AGGREGATE.ITEMS, AGGREGATE.ITEMS.plus(1))
                        .where(AGGREGATE.ID.eq(destId))
                        .execute();

            } else {
                // Create a new aggregate to add this data collection to.
                destId = context
                        .insertInto(
                                AGGREGATE,
                                AGGREGATE.FEED_NAME,
                                AGGREGATE.TYPE_NAME,
                                AGGREGATE.BYTE_SIZE,
                                AGGREGATE.ITEMS,
                                AGGREGATE.CREATE_TIME_MS)
                        .values(collection.feedName,
                                collection.typeName,
                                collection.totalSize,
                                1,
                                System.currentTimeMillis())
                        .returning(AGGREGATE.ID)
                        .fetchOne()
                        .getId();
            }

            // Add the data collection.
            context
                    .insertInto(AGGREGATE_ITEM, AGGREGATE_ITEM.FK_AGGREGATE_ID, AGGREGATE_ITEM.FK_SOURCE_ITEM_ID)
                    .values(destId, collection.dataId)
                    .execute();

            // Mark data collection as added.
            context
                    .update(SOURCE_ITEM)
                    .set(SOURCE_ITEM.IN_AGGREGATE, true)
                    .where(SOURCE_ITEM.ID.eq(collection.dataId))
                    .execute();
        });
    }

    private void closeOldAggregates(final long now) {
        if (lastClosedAggregates == -1 ||
                now > lastClosedAggregates - config.getAggregationFrequency().toMillis()) {
            lastClosedAggregates = now;

            final long oldest = now - config.getMaxAggregateAge().toMillis();

            final Condition condition = DSL.and(
                    DSL.or(
                            AGGREGATE.ITEMS.greaterOrEqual(config.getMaxItemsPerAggregate()),
                            AGGREGATE.BYTE_SIZE.greaterOrEqual(config.getMaxUncompressedByteSize()),
                            AGGREGATE.CREATE_TIME_MS.lessOrEqual(oldest)
                    ),
                    AGGREGATE.COMPLETE.eq(false)
            );

            closeDestinations(condition);
        }
    }

    private synchronized int closeDestinations(final Condition condition) {
        return JooqUtil.contextResult(connProvider, context -> context
                .update(AGGREGATE)
                .set(AGGREGATE.COMPLETE, true)
                .where(condition)
                .execute());
    }

    private static class AggregateDataCollection {

        private final int dataId;
        private final String feedName;
        private final String typeName;
        private long totalSize;

        public AggregateDataCollection(final int dataId,
                                       final String feedName,
                                       final String typeName) {
            this.dataId = dataId;
            this.feedName = feedName;
            this.typeName = typeName;
        }

        public void addEntry(final long entrySize) {
            totalSize += entrySize;
        }
    }
}