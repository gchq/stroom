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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

public class Aggregator {

    private final ProxyRepoDbConnProvider connProvider;
    private final AggregatorConfig config;
    private AggregateDataCollection currentCollection;
    private long lastClosedAggregates = -1;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    Aggregator(final ProxyRepoDbConnProvider connProvider,
               final AggregatorConfig config) {
        this.connProvider = connProvider;
        this.config = config;
    }

    public void aggregate() {
        // Start by trying to close old aggregates.
        closeOldAggregates();

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
                    .where(SOURCE_ITEM.AGGREGATED.isFalse())
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
        }
    }

    public void aggregate(final int sourceId) {
        // Start by trying to close old aggregates.
        closeOldAggregates();

        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all data items that have not been added to aggregate destinations.
            try (final Stream<Record4<Integer, String, String, Long>> stream = context
                    .select(SOURCE_ITEM.ID,
                            SOURCE_ITEM.FEED_NAME,
                            SOURCE_ITEM.TYPE_NAME,
                            SOURCE_ENTRY.BYTE_SIZE)
                    .from(SOURCE_ITEM)
                    .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId))
                    .and(SOURCE_ITEM.AGGREGATED.isFalse())
                    .orderBy(SOURCE_ITEM.NUMBER)
                    .stream()) {
                stream.forEach(record -> {
                    final int dataId = record.get(SOURCE_ITEM.ID);
                    final String feedName = record.get(SOURCE_ITEM.FEED_NAME);
                    final String typeName = record.get(SOURCE_ITEM.TYPE_NAME);
                    final long byteSize = record.get(SOURCE_ENTRY.BYTE_SIZE);

                    if (currentCollection != null && currentCollection.dataId != dataId) {
                        addCollection(currentCollection);
                        currentCollection = null;
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
        }
    }

    private synchronized void addCollection(final AggregateDataCollection collection) {
        final long maxUncompressedByteSize = config.getMaxUncompressedByteSize();
        final int maxItemsPerAggregate = config.getMaxItemsPerAggregate();
        final long maxAggregateSize = Math.max(0, maxUncompressedByteSize - collection.totalSize);

        JooqUtil.transaction(connProvider, context -> {
            // See if we can get an existing aggregate that will fit this data collection.
            final Optional<AggregateRecord> optionalRecord = context
                    .selectFrom(AGGREGATE)
                    .where(AGGREGATE.FEED_NAME.equal(collection.feedName))
                    .and(AGGREGATE.TYPE_NAME.equal(collection.typeName))
                    .and(AGGREGATE.BYTE_SIZE.lessOrEqual(maxAggregateSize))
                    .and(AGGREGATE.ITEMS.lessThan(maxItemsPerAggregate))
                    .and(AGGREGATE.COMPLETE.isFalse())
                    .orderBy(AGGREGATE.CREATE_TIME_MS)
                    .fetchOptional();

            int aggregateId;
            if (optionalRecord.isPresent()) {
                aggregateId = optionalRecord.get().getId();

                // We have somewhere we can add the data collection so add it to the aggregate.
                context
                        .update(AGGREGATE)
                        .set(AGGREGATE.BYTE_SIZE, AGGREGATE.BYTE_SIZE.plus(collection.totalSize))
                        .set(AGGREGATE.ITEMS, AGGREGATE.ITEMS.plus(1))
                        .where(AGGREGATE.ID.eq(aggregateId))
                        .execute();

            } else {
                // Create a new aggregate to add this data collection to.
                aggregateId = context
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
                        .fetchOptional()
                        .map(AggregateRecord::getId)
                        .orElse(-1);
            }

            // Add the data collection.
            context
                    .insertInto(AGGREGATE_ITEM, AGGREGATE_ITEM.FK_AGGREGATE_ID, AGGREGATE_ITEM.FK_SOURCE_ITEM_ID)
                    .values(aggregateId, collection.dataId)
                    .execute();

            // Mark data collection as added.
            context
                    .update(SOURCE_ITEM)
                    .set(SOURCE_ITEM.AGGREGATED, true)
                    .where(SOURCE_ITEM.ID.eq(collection.dataId))
                    .execute();
        });

        // Close any old aggregates.
        closeOldAggregates();
    }

    private void closeOldAggregates() {
        final long now = System.currentTimeMillis();
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

            final int count = closeAggregates(condition);

            // If we have closed some aggregates then let others know there are some available.
            if (count > 0) {
                fireChange();
            }
        }
    }

    private synchronized int closeAggregates(final Condition condition) {
        return JooqUtil.contextResult(connProvider, context -> context
                .update(AGGREGATE)
                .set(AGGREGATE.COMPLETE, true)
                .where(condition)
                .execute());
    }

    private void fireChange() {
        listeners.forEach(listener -> fireChange());
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public interface ChangeListener {
        void onChange();
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