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

package stroom.proxy.repo.db;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.proxy.repo.db.jooq.tables.records.ZipDestRecord;

import org.jooq.Condition;
import org.jooq.Record3;
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

import static stroom.proxy.repo.db.jooq.tables.ZipData.ZIP_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipDest.ZIP_DEST;
import static stroom.proxy.repo.db.jooq.tables.ZipDestData.ZIP_DEST_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipEntry.ZIP_ENTRY;

class Aggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Aggregator.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final ZipInfoStoreDao zipInfoStoreDao;
    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private ZipDataCollection currentCollection;

    @Inject
    Aggregator(final ZipInfoStoreDao zipInfoStoreDao,
               final ProxyRepoDbConnProvider connProvider,
               final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig) {
        this.zipInfoStoreDao = zipInfoStoreDao;
        this.connProvider = connProvider;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;

        // Start aggregation.
        aggregate();
    }

    public void aggregate() {
        final long start = System.currentTimeMillis();

        // Start by trying to close old destinations.
        closeOldDestinations(start);

        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all data items that have not been added to aggregate destinations.
            try (final Stream<Record4<Integer, String, String, Long>> stream = context
                    .select(ZIP_DATA.ID,
                            ZIP_DATA.FEED_NAME,
                            ZIP_DATA.TYPE_NAME,
                            ZIP_ENTRY.BYTE_SIZE)
                    .from(ZIP_DATA)
                    .join(ZIP_ENTRY).on(ZIP_ENTRY.FK_ZIP_DATA_ID.eq(ZIP_DATA.ID))
                    .and(ZIP_DATA.HAS_DEST.isFalse())
                    .orderBy(ZIP_DATA.FEED_NAME, ZIP_DATA.ID)
                    .stream()) {
                stream.forEach(record -> {
                    final int dataId = record.get(ZIP_DATA.ID);
                    final String feedName = record.get(ZIP_DATA.FEED_NAME);
                    final String typeName = record.get(ZIP_DATA.TYPE_NAME);
                    final long byteSize = record.get(ZIP_ENTRY.BYTE_SIZE);

                    if (currentCollection != null && currentCollection.dataId != dataId) {
                        addCollection(currentCollection);
                        currentCollection = null;
                    }

                    if (currentCollection == null) {
                        currentCollection = new ZipDataCollection(
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

        // Run the aggregation again on the next set of data.
        final long frequency = proxyRepositoryReaderConfig.getAggregationFrequency().toMillis();
        // How long were we running for?
        final long duration = System.currentTimeMillis() - start;
        final long delay = Math.max(0, frequency - duration);
        executorService.schedule(this::aggregate, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void addCollection(final ZipDataCollection collection) {
        final long maxUncompressedByteSize = proxyRepositoryReaderConfig.getMaxUncompressedByteSize();
        final int maxItemsPerAggregate = proxyRepositoryReaderConfig.getMaxItemsPerAggregate();
        final long maxDestSize = Math.max(0, maxUncompressedByteSize - collection.totalSize);

        JooqUtil.transaction(connProvider, context -> {
            // See if we can get an existing destination that will fit this data collection.
            final Optional<ZipDestRecord> optionalRecord = context
                    .selectFrom(ZIP_DEST)
                    .where(ZIP_DEST.FEED_NAME.equal(collection.feedName))
                    .and(ZIP_DEST.TYPE_NAME.equal(collection.typeName))
                    .and(ZIP_DEST.BYTE_SIZE.lessOrEqual(maxDestSize))
                    .and(ZIP_DEST.ITEMS.lessThan(maxItemsPerAggregate))
                    .and(ZIP_DEST.COMPLETE.isFalse())
                    .orderBy(ZIP_DEST.CREATE_TIME_MS)
                    .fetchOptional();

            int destId;
            if (optionalRecord.isPresent()) {
                destId = optionalRecord.get().getId();

                // We have somewhere we can add the data collection so add it to the dest.
                context
                        .update(ZIP_DEST)
                        .set(ZIP_DEST.BYTE_SIZE, ZIP_DEST.BYTE_SIZE.plus(collection.totalSize))
                        .set(ZIP_DEST.ITEMS, ZIP_DEST.ITEMS.plus(1))
                        .where(ZIP_DEST.ID.eq(destId))
                        .execute();

            } else {
                // Create a new dest to add this data collection to.
                destId = context
                        .insertInto(
                                ZIP_DEST,
                                ZIP_DEST.FEED_NAME,
                                ZIP_DEST.TYPE_NAME,
                                ZIP_DEST.BYTE_SIZE,
                                ZIP_DEST.ITEMS,
                                ZIP_DEST.CREATE_TIME_MS)
                        .values(collection.feedName,
                                collection.typeName,
                                collection.totalSize,
                                1,
                                System.currentTimeMillis())
                        .returning(ZIP_DEST.ID)
                        .fetchOne()
                        .getId();
            }

            // Add the data collection.
            context
                    .insertInto(ZIP_DEST_DATA, ZIP_DEST_DATA.FK_ZIP_DEST_ID, ZIP_DEST_DATA.FK_ZIP_DATA_ID)
                    .values(destId, collection.dataId)
                    .execute();

            // Mark data collection as added.
            context
                    .update(ZIP_DATA)
                    .set(ZIP_DATA.HAS_DEST, true)
                    .where(ZIP_DATA.ID.eq(collection.dataId))
                    .execute();
        });
    }

    private void closeOldDestinations(final long now) {
        final long oldest = now - proxyRepositoryReaderConfig.getMaxAggregateAge().toMillis();

        final Condition condition = DSL.and(
                DSL.or(
                        ZIP_DEST.ITEMS.greaterOrEqual(proxyRepositoryReaderConfig.getMaxItemsPerAggregate()),
                        ZIP_DEST.BYTE_SIZE.greaterOrEqual(proxyRepositoryReaderConfig.getMaxUncompressedByteSize()),
                        ZIP_DEST.CREATE_TIME_MS.lessOrEqual(oldest)
                ),
                ZIP_DEST.COMPLETE.eq(false)
        );

        closeDestinations(condition);
    }

    private synchronized int closeDestinations(final Condition condition) {
        return JooqUtil.contextResult(connProvider, context -> context
                .update(ZIP_DEST)
                .set(ZIP_DEST.COMPLETE, true)
                .where(condition)
                .execute());
    }

    private static class ZipDataCollection {

        private final int dataId;
        private final String feedName;
        private final String typeName;
        private long totalSize;

        public ZipDataCollection(final int dataId,
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