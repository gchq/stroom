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
import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.AggregateDao.Aggregate;
import stroom.proxy.repo.dao.AggregateDao.SourceEntry;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.ForwardUrlDao;
import stroom.proxy.repo.dao.SourceDao;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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

@Singleton
public class AggregateForwarder implements Forwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateForwarder.class);
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

    private final SourceDao sourceDao;
    private final AggregateDao aggregateDao;
    private final ForwardAggregateDao forwardAggregateDao;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardUrlDao forwardUrlDao;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final Path repoDir;

    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private volatile String hostName = null;
    private volatile boolean shutdown;


    @Inject
    AggregateForwarder(final SourceDao sourceDao,
                       final AggregateDao aggregateDao,
                       final ForwardAggregateDao forwardAggregateDao,
                       final ForwardSourceDao forwardSourceDao,
                       final ForwardUrlDao forwardUrlDao,
                       final ForwarderDestinations forwarderDestinations,
                       final RepoDirProvider repoDirProvider) {

        this.sourceDao = sourceDao;
        this.aggregateDao = aggregateDao;
        this.forwardAggregateDao = forwardAggregateDao;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardUrlDao = forwardUrlDao;

        this.forwarderDestinations = forwarderDestinations;
        this.repoDir = repoDirProvider.get();
    }

    @Override
    public synchronized int cleanup() {
        int total = 0;

        // Delete any source forward attempts as we are no longer forwarding source directly.
        total += forwardSourceDao.deleteAll();

        // Update sources to ensure they aren't marked as having had forwarding errors.
        total += sourceDao.resetFailedForwards();

        return total;
    }

    @Override
    public int retryFailures() {
        int count = 0;

        // Allow the system to retry all failed destinations.
        count += forwardAggregateDao.deleteFailedForwards();

        // Reconsider failed aggregates for forwarding.
        count += aggregateDao.resetFailedForwards();

        return count;
    }

    @Override
    public synchronized void forward() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final List<Aggregate> aggregates = aggregateDao.getCompletedAggregates(BATCH_SIZE);
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            aggregates.forEach(aggregate -> {
                if (!shutdown) {
                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture = forwardAggregate(aggregate);
                    futures.add(completableFuture);
                }
            });

            // Wait for all forwarding jobs to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop forwarding if the last query did not return a result as big as the batch size.
            if (aggregates.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    private CompletableFuture<Void> forwardAggregate(final Aggregate aggregate) {
        // See if this data has been sent to all forward URLs.
        final Map<Integer, Boolean> successMap = forwardAggregateDao.getForwardingState(aggregate.getAggregateId());

        // Forward to all remaining places.
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final AtomicInteger failureCount = new AtomicInteger();
        for (final Entry<Integer, String> entry : forwardUrlDao.getForwardIdUrlMap().entrySet()) {
            final int forwardId = entry.getKey();

            final Boolean previousSuccess = successMap.get(forwardId);
            if (previousSuccess == Boolean.FALSE) {
                failureCount.incrementAndGet();

            } else if (previousSuccess == null) {
                final String forwardUrl = entry.getValue();
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                    final boolean success = forwardAggregateData(
                            aggregate,
                            forwardId,
                            forwardUrl);
                    if (!success) {
                        failureCount.incrementAndGet();
                    }
                }, executor);
                futures.add(completableFuture);
            }
        }

        // When all futures complete we want to try and delete the aggregate.
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> {
                    // Delete the aggregate if we have successfully forwarded to all destinations.
                    if (failureCount.get() == 0) {
                        setForwardSuccess(aggregate.getAggregateId());
                    } else {
                        // Mark the aggregate as having errors so we don't keep endlessly trying to send it.
                        aggregateDao.setForwardError(aggregate.getAggregateId());
                    }
                }, executor);
    }


    boolean forwardAggregateData(final Aggregate aggregate,
                                 final int forwardUrlId,
                                 final String forwardUrl) {
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final List<SourceEntry> entries = aggregateDao.fetchSourceEntries(aggregate.getAggregateId());
        if (entries.size() > 0) {
            final long thisPostId = proxyForwardId.incrementAndGet();
            final String info = thisPostId + " " + aggregate.getFeedName() + " - " + aggregate.getTypeName();
            LOGGER.debug(() -> "processFeedFiles() - proxyForwardId " + info);

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
            attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
            attributeMap.put(StandardHeaderArguments.FEED, aggregate.getFeedName());
            if (aggregate.getTypeName() != null) {
                attributeMap.put(StandardHeaderArguments.TYPE, aggregate.getTypeName());
            }
            if (LOGGER.isDebugEnabled()) {
                attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
            }

            final StreamHandlers streamHandlers = forwarderDestinations.getProvider(forwardUrl);

            // Start the POST
            try {
                streamHandlers.handle(aggregate.getFeedName(), aggregate.getTypeName(), attributeMap, handler -> {
                    EntryKey lastSourceKey = null;
                    String targetName = null;
                    long sequenceId = 1;

                    for (final SourceEntry entry : entries) {
                        // Send no more if told to finish
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.info(() -> "processFeedFiles() - Quitting early as we have been told to stop");
                            throw new RuntimeException(
                                    "processFeedFiles() - Quitting early as we have been told to stop");
                        }

                        final String sourcePath = entry.getSourcePath();
                        final String sourceName = entry.getName();
                        final String extension = entry.getExtension();
                        final EntryKey sourceKey = new EntryKey(sourcePath, sourceName);

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
                LOGGER.warn(() -> "processFeedFiles() - Failed to send to feed " +
                        aggregate.getFeedName() +
                        " ( " +
                        ex +
                        ")");
                LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
            }
        } else {
            success.set(true);
        }

        // Record that we sent the data or if there was no data to send.
        forwardAggregateDao.createForwardAggregateRecord(
                forwardUrlId,
                aggregate.getAggregateId(),
                success.get(),
                error.get());

        return success.get();
    }

    void setForwardSuccess(final long aggregateId) {
        LOGGER.debug(() -> "deleteAggregate: " + aggregateId);

        forwardAggregateDao.setForwardSuccess(aggregateId);

        // Once we have deleted an aggregate cleanup operation might want to run.
        fireChange();
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
        forwardAggregateDao.clear();
        forwardUrlDao.clear();
    }

    private void fireChange() {
        changeListeners.forEach(ChangeListener::onChange);
    }

    @Override
    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
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
