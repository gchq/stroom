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
import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.ForwardUrlDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import java.nio.file.Path;
import java.util.ArrayList;
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
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

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
            20,
            Integer.MAX_VALUE,
            10,
            TimeUnit.MINUTES,
            threadFactory);

    private final SourceDao sourceDao;
    private final SourceEntryDao sourceEntryDao;
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
    SourceForwarder(final SourceDao sourceDao,
                    final SourceEntryDao sourceEntryDao,
                    final AggregateDao aggregateDao,
                    final ForwardAggregateDao forwardAggregateDao,
                    final ForwardSourceDao forwardSourceDao,
                    final ForwardUrlDao forwardUrlDao,
                    final ForwarderDestinations forwarderDestinations,
                    final RepoDirProvider repoDirProvider) {

        this.sourceDao = sourceDao;
        this.sourceEntryDao = sourceEntryDao;
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

        // Delete any aggregate forward attempts as we are no longer forwarding aggregates.
        total += forwardAggregateDao.deleteAll();
        total += aggregateDao.deleteAll();
        total += sourceEntryDao.deleteAll();
        total += sourceDao.resetExamined();

        return total;
    }

    @Override
    public int retryFailures() {
        int count = 0;

        // Allow the system to retry all failed destinations.
        count += forwardSourceDao.deleteFailedForwards();

        // Reconsider failed sources for forwarding.
        count += sourceDao.resetFailedForwards();

        return count;
    }

    @Override
    public synchronized void forward() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final List<Source> sources = sourceDao.getCompletedSources(BATCH_SIZE);

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            sources.forEach(source -> {
                if (!shutdown) {
                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture = forwardSource(source);
                    futures.add(completableFuture);
                }
            });

            // Wait for all forwarding jobs to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop forwarding if the last query did not return a result as big as the batch size.
            if (sources.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    private CompletableFuture<Void> forwardSource(final Source source) {
        // See if this data has been sent to all forward URLs.
        final Map<Integer, Boolean> successMap = forwardSourceDao.getForwardingState(source.getSourceId());

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
                    final boolean success = forwardSourceData(
                            source,
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
                        setForwardSuccess(source.getSourceId());
                    } else {
                        // Mark the source as having errors so we don't keep endlessly trying to send it.
                        sourceDao.setForwardError(source.getSourceId());
                    }
                }, executor);
    }

    boolean forwardSourceData(final Source source,
                              final int forwardUrlId,
                              final String forwardUrl) {
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final long thisPostId = proxyForwardId.incrementAndGet();
        final String info = thisPostId + " " + source.getFeedName() + " - " + source.getTypeName();
        LOGGER.debug(() -> "processFeedFiles() - proxyForwardId " + info);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
        attributeMap.put(StandardHeaderArguments.FEED, source.getFeedName());
        if (source.getTypeName() != null) {
            attributeMap.put(StandardHeaderArguments.TYPE, source.getTypeName());
        }
        if (LOGGER.isDebugEnabled()) {
            attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final StreamHandlers streamHandlers = forwarderDestinations.getProvider(forwardUrl);
        final Path zipFilePath = repoDir.resolve(source.getSourcePath());

        final Consumer<Long> progressHandler = new ProgressHandler("Sending" +
                zipFilePath);

        // Start the POST
        try {
            streamHandlers.handle(source.getFeedName(), source.getTypeName(), attributeMap, handler -> {
                // Use the Stroom stream processor to send zip entries in a consistent order.
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap, handler, progressHandler);
                stroomStreamProcessor.processZipFile(zipFilePath);
            });

            success.set(true);

        } catch (final RuntimeException ex) {
            error.set(ex.getMessage());
            LOGGER.warn(() -> "processFeedFiles() - Failed to send to feed " + source.getFeedName() + " ( " + ex + ")");
            LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
        }

        // Record that we sent the data or if there was no data to send.
        forwardSourceDao.createForwardSourceRecord(forwardUrlId, source.getSourceId(), success.get(), error.get());

        return success.get();
    }

    void setForwardSuccess(final long sourceId) {
        LOGGER.debug(() -> "deleteSource: " + sourceId);

        forwardSourceDao.setForwardSuccess(sourceId);

        // Once we have deleted a source cleanup operation might want to run.
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
        forwardSourceDao.clear();
        forwardUrlDao.clear();
    }

    private void fireChange() {
        changeListeners.forEach(ChangeListener::onChange);
    }

    @Override
    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }
}
