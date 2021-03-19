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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
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
import org.jooq.Record4;
import org.jooq.Result;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardSource.FORWARD_SOURCE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SourceForwarder implements Forwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceForwarder.class);
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
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final ForwardUrlService forwardUrlService;
    private final Path repoDir;

    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private volatile String hostName = null;
    private volatile boolean shutdown;

    private final AtomicLong forwardSourceId = new AtomicLong();

    @Inject
    SourceForwarder(final ProxyRepoDbConnProvider connProvider,
                    final ForwarderDestinations forwarderDestinations,
                    final ForwardUrlService forwardUrlService,
                    final RepoDirProvider repoDirProvider) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.forwarderDestinations = forwarderDestinations;
        this.forwardUrlService = forwardUrlService;
        this.repoDir = repoDirProvider.get();

        init();
    }

    private void init() {
        final long maxForwardSourceRecordId = jooq
                .getMaxId(FORWARD_SOURCE, FORWARD_SOURCE.ID).orElse(0L);
        forwardSourceId.set(maxForwardSourceRecordId);
    }

    @Override
    public synchronized int cleanup() {
        return jooq.transactionResult(context -> {
            int total = 0;

            // Delete any aggregate forward attempts as we are no longer forwarding aggregates.
            total += context
                    .deleteFrom(FORWARD_AGGREGATE)
                    .execute();
            total += context
                    .deleteFrom(AGGREGATE_ITEM)
                    .execute();
            total += context
                    .deleteFrom(AGGREGATE)
                    .execute();
            total += context
                    .deleteFrom(SOURCE_ENTRY)
                    .execute();
            total += context
                    .deleteFrom(SOURCE_ITEM)
                    .execute();
            total += context
                    .update(SOURCE)
                    .set(SOURCE.EXAMINED, false)
                    .execute();

            return total;
        });
    }

    @Override
    public int retryFailures() {
        int count = 0;

        // Allow the system to retry all failed destinations.
        count += jooq.contextResult(context -> context
                .deleteFrom(FORWARD_SOURCE)
                .where(FORWARD_SOURCE.SUCCESS.isFalse())
                .execute());

        // Reconsider failed aggregates for forwarding.
        count += jooq.contextResult(context -> context
                .update(SOURCE)
                .set(SOURCE.FORWARD_ERROR, false)
                .where(SOURCE.FORWARD_ERROR.isTrue())
                .execute());

        return count;
    }

    @Override
    public synchronized void forward() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final Result<Record4<Long, String, String, String>> result = getCompletedSources(BATCH_SIZE);

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            result.forEach(record -> {
                if (!shutdown) {
                    final long sourceId = record.get(SOURCE.ID);
                    final String sourcePath = record.get(SOURCE.PATH);
                    final String feedName = record.get(SOURCE.FEED_NAME);
                    final String typeName = record.get(SOURCE.TYPE_NAME);

                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture = forwardSource(
                            sourceId,
                            sourcePath,
                            feedName,
                            typeName);
                    futures.add(completableFuture);
                }
            });

            // Wait for all forwarding jobs to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop forwarding if the last query did not return a result as big as the batch size.
            if (result.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    private CompletableFuture<Void> forwardSource(final long sourceId,
                                                  final String sourcePath,
                                                  final String feedName,
                                                  final String typeName) {
        // See if this data has been sent to all forward URLs.
        final Map<Integer, Boolean> successMap = jooq.contextResult(context -> context
                .select(FORWARD_SOURCE.FK_FORWARD_URL_ID, FORWARD_SOURCE.SUCCESS)
                .from(FORWARD_SOURCE)
                .where(FORWARD_SOURCE.FK_SOURCE_ID.eq(sourceId))
                .fetch()
                .stream()
                .collect(
                        Collectors.toMap(
                                r -> r.get(FORWARD_SOURCE.FK_FORWARD_URL_ID),
                                r -> r.get(FORWARD_SOURCE.SUCCESS))));

        // Forward to all remaining places.
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final AtomicInteger failureCount = new AtomicInteger();
        for (final Entry<Integer, String> entry : forwardUrlService.getForwardIdUrlMap().entrySet()) {
            final int forwardId = entry.getKey();

            final Boolean previousSuccess = successMap.get(forwardId);
            if (previousSuccess == Boolean.FALSE) {
                failureCount.incrementAndGet();

            } else if (previousSuccess == null) {
                final String forwardUrl = entry.getValue();
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                    final boolean success = forwardSourceData(
                            sourceId,
                            sourcePath,
                            feedName,
                            typeName,
                            forwardId,
                            forwardUrl);
                    if (!success) {
                        failureCount.incrementAndGet();
                    }
                }, executor);
                futures.add(completableFuture);
            }
        }

        // When all futures complete we want to try and delete the source.
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> {
                    // Delete the source if we have successfully forwarded to all destinations.
                    if (failureCount.get() == 0) {
                        deleteSource(sourceId);
                    } else {
                        // Mark the source as having errors so we don't keep endlessly trying to send it.
                        jooq.context(context -> context
                                .update(SOURCE)
                                .set(SOURCE.FORWARD_ERROR, true)
                                .where(SOURCE.ID.eq(sourceId))
                                .execute());
                    }
                }, executor);
    }

    boolean forwardSourceData(final long sourceId,
                              final String sourcePath,
                              final String feedName,
                              final String typeName,
                              final int forwardUrlId,
                              final String forwardUrl) {
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final long thisPostId = proxyForwardId.incrementAndGet();
        final String info = thisPostId + " " + feedName + " - " + typeName;
        LOGGER.debug(() -> "processFeedFiles() - proxyForwardId " + info);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        if (typeName != null) {
            attributeMap.put(StandardHeaderArguments.TYPE, typeName);
        }
        if (LOGGER.isDebugEnabled()) {
            attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final StreamHandlers streamHandlers = forwarderDestinations.getProvider(forwardUrl);

        // Start the POST
        try {
            streamHandlers.handle(feedName, typeName, attributeMap, handler -> {
                final Path zipFilePath = repoDir.resolve(sourcePath);

                try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipFilePath))) {
                    final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                    while (entries.hasMoreElements()) {
                        final ZipArchiveEntry zipArchiveEntry = entries.nextElement();

                        try (final ByteCountInputStream inputStream =
                                new ByteCountInputStream(zipFile.getInputStream(zipArchiveEntry))) {
                            LOGGER.debug(() -> "sendEntry() - " + zipArchiveEntry.getName());

                            handler.addEntry(zipArchiveEntry.getName(), inputStream);
                            final long totalRead = inputStream.getCount();

                            LOGGER.trace(() -> "sendEntry() - " +
                                    zipArchiveEntry.getName() +
                                    " " +
                                    ModelStringUtil.formatIECByteSizeString(
                                            totalRead));

                            if (totalRead == 0) {
                                LOGGER.warn(() -> "sendEntry() - " + zipArchiveEntry.getName() + " IS BLANK");
                            }
                            LOGGER.debug(() -> "sendEntry() - " + zipArchiveEntry.getName() + " size is " + totalRead);
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }

                success.set(true);
            });
        } catch (final RuntimeException ex) {
            error.set(ex.getMessage());
            LOGGER.warn(() -> "processFeedFiles() - Failed to send to feed " + feedName + " ( " + ex + ")");
            LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
        }

        // Record that we sent the data or if there was no data to send.
        createForwardSourceRecord(forwardUrlId, sourceId, success.get(), error.get());

        return success.get();
    }

    /**
     * Create a record of the fact that we forwarded a source or at least tried to.
     */
    void createForwardSourceRecord(final int forwardUrlId,
                                   final long sourceId,
                                   final boolean success,
                                   final String error) {
        jooq.context(context -> context
                .insertInto(
                        FORWARD_SOURCE,
                        FORWARD_SOURCE.ID,
                        FORWARD_SOURCE.FK_FORWARD_URL_ID,
                        FORWARD_SOURCE.FK_SOURCE_ID,
                        FORWARD_SOURCE.SUCCESS,
                        FORWARD_SOURCE.ERROR)
                .values(forwardSourceId.incrementAndGet(), forwardUrlId, sourceId, success, error)
                .execute());
    }

    void deleteSource(final long sourceId) {
        LOGGER.debug(() -> "deleteSource: " + sourceId);

        jooq.transaction(context -> {
            context
                    .deleteFrom(FORWARD_SOURCE)
                    .where(FORWARD_SOURCE.FK_SOURCE_ID.equal(sourceId))
                    .execute();

            // Record the source as forwarded so it can be deleted by Cleanup.
            context
                    .update(SOURCE)
                    .set(SOURCE.FORWARDED, true)
                    .where(SOURCE.ID.eq(sourceId))
                    .execute();
        });

        // Once we have deleted a source cleanup operation might want to run.
        fireChange();
    }

    Result<Record4<Long, String, String, String>> getCompletedSources(final int limit) {
        return jooq.contextResult(context -> context
                // Get all completed sources.
                .select(SOURCE.ID, SOURCE.PATH, SOURCE.FEED_NAME, SOURCE.TYPE_NAME)
                .from(SOURCE)
                .where(SOURCE.FORWARD_ERROR.isFalse())
                .orderBy(SOURCE.LAST_MODIFIED_TIME_MS)
                .limit(limit)
                .fetch());
    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    @Override
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

    public void clear() {
        jooq.deleteAll(FORWARD_SOURCE);
        jooq.deleteAll(FORWARD_URL);

        jooq
                .getMaxId(FORWARD_SOURCE, FORWARD_SOURCE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        jooq
                .getMaxId(FORWARD_URL, FORWARD_URL.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });

        init();
    }

    private void fireChange() {
        changeListeners.forEach(ChangeListener::onChange);
    }

    @Override
    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }
}
