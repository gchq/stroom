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

import stroom.data.zip.StroomFileNameUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.db.jooq.tables.records.ForwardUrlRecord;
import stroom.receive.common.StreamHandlers;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.Record3;
import org.jooq.Result;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class Forwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Forwarder.class);
    private static final String PROXY_FORWARD_ID = "ProxyForwardId";
    private static final int BATCH_SIZE = 1000000;

    private final ThreadFactory threadFactory = new CustomThreadFactory(
            "Forward Data",
            StroomThreadGroup.instance(),
            Thread.NORM_PRIORITY - 1);
    private final ExecutorService executor = ScalingThreadPoolExecutor.newScalingThreadPool(
            1,
            10,
            100,
            10,
            TimeUnit.MINUTES,
            threadFactory);

    private final SqliteJooqHelper jooq;
    private final ProxyRepoConfig proxyRepoConfig;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;

    private final Map<Integer, String> forwardIdUrlMap = new HashMap<>();
    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private volatile String hostName = null;
    private volatile boolean shutdown;

    @Inject
    Forwarder(final ProxyRepoDbConnProvider connProvider,
              final ProxyRepoConfig proxyRepoConfig,
              final ForwarderDestinations forwarderDestinations) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.proxyRepoConfig = proxyRepoConfig;
        this.forwarderDestinations = forwarderDestinations;

        if (forwarderDestinations.getDestinationNames().size() > 0) {
            // Create a map of forward URLs to DB ids.
            jooq.context(context -> {
                for (final String destinationName : forwarderDestinations.getDestinationNames()) {
                    final Optional<Integer> optionalId = context
                            .select(FORWARD_URL.ID)
                            .from(FORWARD_URL)
                            .where(FORWARD_URL.URL.equal(destinationName))
                            .fetchOptional()
                            .map(r -> r.get(FORWARD_URL.ID));

                    final int id = optionalId.orElseGet(() -> context
                            .insertInto(FORWARD_URL, FORWARD_URL.URL)
                            .values(destinationName)
                            .returning(FORWARD_URL.ID)
                            .fetchOptional()
                            .map(ForwardUrlRecord::getId)
                            .orElse(-1));

                    forwardIdUrlMap.put(id, destinationName);
                }
            });
        }
    }

    public synchronized void forward() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final Result<Record3<Long, String, String>> result =
                    jooq.contextResult(context -> context
                            // Get all completed aggregates.
                            .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                            .from(AGGREGATE)
                            .where(AGGREGATE.COMPLETE.isTrue())
                            .and(AGGREGATE.FORWARD_ERROR.isFalse())
                            .orderBy(AGGREGATE.CREATE_TIME_MS)
                            .limit(BATCH_SIZE)
                            .fetch());

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            result.forEach(record -> {
                if (!shutdown) {
                    final long aggregateId = record.get(AGGREGATE.ID);
                    final String feedName = record.get(AGGREGATE.FEED_NAME);
                    final String typeName = record.get(AGGREGATE.TYPE_NAME);

                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture = forwardAggregate(aggregateId, feedName, typeName);
                    futures.add(completableFuture);
                }
            });

            // Wait for all forwarding jobs to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop forwarding if the last query did not return a result as big as the batch size.
            if (count.get() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    private CompletableFuture<Void> forwardAggregate(final long aggregateId,
                                                     final String feedName,
                                                     final String typeName) {
        final Map<Integer, String> remainingForwardUrl = new HashMap<>(forwardIdUrlMap);
        final AtomicBoolean previousFailure = new AtomicBoolean();

        // See if this data has been sent to all forward URLs.
        jooq.context(context -> context
                .select(FORWARD_AGGREGATE.FK_FORWARD_URL_ID, FORWARD_AGGREGATE.SUCCESS)
                .from(FORWARD_AGGREGATE)
                .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(aggregateId))
                .fetch()
                .forEach(r -> {
                    remainingForwardUrl.remove(r.get(FORWARD_AGGREGATE.FK_FORWARD_URL_ID));
                    if (!r.get(FORWARD_AGGREGATE.SUCCESS)) {
                        previousFailure.set(true);
                    }
                }));

        // Forward to all remaining places.
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final AtomicInteger successCount = new AtomicInteger();
        for (final Entry<Integer, String> entry : remainingForwardUrl.entrySet()) {
            final int forwardId = entry.getKey();
            final String forwardUrl = entry.getValue();
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                final boolean success = forwardAggregateData(aggregateId, feedName, typeName, forwardId, forwardUrl);
                if (success) {
                    successCount.incrementAndGet();
                }
            }, executor);
            futures.add(completableFuture);
        }

        // When all futures complete we want to try and delete the aggregate.
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> {
                    // Delete the aggregate if we have successfully forwarded to all destinations.
                    if (!previousFailure.get() &&
                            successCount.get() == remainingForwardUrl.size()) {
                        deleteAggregate(aggregateId);
                    } else {
                        // Mark the aggregate as having errors so we don't keep endlessly trying to send it.
                        jooq.context(context -> context
                                .update(AGGREGATE)
                                .set(AGGREGATE.FORWARD_ERROR, true)
                                .where(AGGREGATE.ID.eq(aggregateId))
                                .execute());
                    }
                }, executor);
    }


    private boolean forwardAggregateData(final long aggregateId,
                                         final String feedName,
                                         final String typeName,
                                         final int forwardUrlId,
                                         final String forwardUrl) {
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final List<Record3<String, String, String>> entries = jooq.contextResult(context -> {
            // Get all of the source zip entries that we want to write to the forwarding location.
            return context
                    .select(SOURCE.PATH,
                            SOURCE_ITEM.NAME,
                            SOURCE_ENTRY.EXTENSION)
                    .from(SOURCE)
                    .join(SOURCE_ITEM).on(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))
                    .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .join(AGGREGATE_ITEM).on(AGGREGATE_ITEM.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .join(AGGREGATE).on(AGGREGATE.ID.eq(AGGREGATE_ITEM.FK_AGGREGATE_ID))
                    .where(AGGREGATE.ID.eq(aggregateId))
                    .orderBy(SOURCE.PATH, SOURCE_ITEM.NAME, SOURCE_ENTRY.EXTENSION_TYPE, SOURCE_ENTRY.EXTENSION)
                    .fetch();
        });

        if (entries.size() > 0) {
            final long thisPostId = proxyForwardId.incrementAndGet();
            final String info = thisPostId + " " + feedName + " - " + typeName;
            LOGGER.debug(() -> "processFeedFiles() - proxyForwardId " + info);

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
            attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
            if (LOGGER.isDebugEnabled()) {
                attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
            }

            final StreamHandlers streamHandlers = forwarderDestinations.getProvider(forwardUrl);

            // Start the POST
            try {
                streamHandlers.handle(feedName, typeName, attributeMap, handler -> {
                    EntryKey lastSourceKey = null;
                    String targetName = null;
                    long sequenceId = 1;

                    for (final Record3<String, String, String> record : entries) {
                        // Send no more if told to finish
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.info(() -> "processFeedFiles() - Quitting early as we have been told to stop");
                            throw new RuntimeException(
                                    "processFeedFiles() - Quitting early as we have been told to stop");
                        }

                        final String sourcePath = record.get(SOURCE.PATH);
                        final String sourceName = record.get(SOURCE_ITEM.NAME);
                        final String extension = record.get(SOURCE_ENTRY.EXTENSION);
                        final EntryKey sourceKey = new EntryKey(sourcePath, sourceName);

                        final Path repoDir = Paths.get(proxyRepoConfig.getRepoDir());
                        final Path zipFilePath = repoDir.resolve(sourcePath);

                        try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipFilePath))) {
                            // If the source name has changed then change the target name.
                            if (lastSourceKey == null || !lastSourceKey.equals(sourceKey)) {
                                lastSourceKey = sourceKey;
                                targetName = StroomFileNameUtil.getIdPath(sequenceId++);
                            }

                            final String fullSourceName = sourceName + extension;
                            final String fullTargetName = targetName + extension;

                            final ZipArchiveEntry zipArchiveEntry = zipFile.getEntry(fullSourceName);
                            try (final ByteCountInputStream inputStream =
                                    new ByteCountInputStream(zipFile.getInputStream(zipArchiveEntry))) {
                                LOGGER.debug(() -> "sendEntry() - " + fullTargetName);

                                handler.addEntry(targetName + extension, inputStream);
                                final long totalRead = inputStream.getCount();

                                LOGGER.trace(() -> "sendEntry() - " +
                                        fullTargetName +
                                        " " +
                                        ModelStringUtil.formatIECByteSizeString(
                                                totalRead));

                                if (totalRead == 0) {
                                    LOGGER.warn(() -> "sendEntry() - " + fullTargetName + " IS BLANK");
                                }
                                LOGGER.debug(() -> "sendEntry() - " + fullTargetName + " size is " + totalRead);
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    success.set(true);
                });
            } catch (final RuntimeException ex) {
                error.set(ex.getMessage());
                LOGGER.warn(() -> "processFeedFiles() - Failed to send to feed " + feedName + " ( " + ex + ")");
                LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
            }
        } else {
            success.set(true);
        }

        // Record that we sent the data or if there was no data to send.
        jooq.context(context -> context
                .insertInto(
                        FORWARD_AGGREGATE,
                        FORWARD_AGGREGATE.FK_FORWARD_URL_ID,
                        FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                        FORWARD_AGGREGATE.SUCCESS,
                        FORWARD_AGGREGATE.ERROR)
                .values(forwardUrlId, aggregateId, success.get(), error.get())
                .execute());

        return success.get();
    }

    private void deleteAggregate(final long aggregateId) {
        jooq.transaction(context -> {
            context
                    .deleteFrom(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.equal(aggregateId))
                    .execute();

            context
                    .deleteFrom(AGGREGATE_ITEM)
                    .where(AGGREGATE_ITEM.FK_AGGREGATE_ID.equal(aggregateId))
                    .execute();

            context
                    .deleteFrom(AGGREGATE)
                    .where(AGGREGATE.ID.equal(aggregateId))
                    .execute();
        });

        // Once we have deleted an aggregate a cleanup operation might want to run.
        fireChange();
    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                LOGGER.debug(() -> "Shutting down");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fireChange() {
        changeListeners.forEach(ChangeListener::onChange);
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange();
    }

    private static class EntryKey {
        private final String path;
        private final String name;

        public EntryKey(final String path, final String name) {
            this.path = path;
            this.name = name;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final EntryKey entryKey = (EntryKey) o;
            return path.equals(entryKey.path) && name.equals(entryKey.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }
    }
}
