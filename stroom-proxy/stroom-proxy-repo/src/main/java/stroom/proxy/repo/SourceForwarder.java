/*
 * Copyright 2024 Crown Copyright
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
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;
import stroom.receive.common.StreamHandlers;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.string.CIKeys;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class SourceForwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceForwarder.class);

    private final FeedDao feedDao;
    private final RepoSources sources;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardDestinations forwardDestinations;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final FailureDestinations failureDestinations;
    private final Sender sender;
    private final ProgressLog progressLog;
    private final ForwardRetryConfig forwardRetryConfig;

    private volatile String hostName = null;

    @Inject
    SourceForwarder(final FeedDao feedDao,
                    final RepoSources sources,
                    final ForwardSourceDao forwardSourceDao,
                    final ForwardDestinations forwardDestinations,
                    final ForwarderDestinations forwarderDestinations,
                    final FailureDestinations failureDestinations,
                    final Sender sender,
                    final ProgressLog progressLog,
                    final ForwardRetryConfig forwardRetryConfig) {

        this.feedDao = feedDao;
        this.sources = sources;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardDestinations = forwardDestinations;

        this.forwarderDestinations = forwarderDestinations;
        this.failureDestinations = failureDestinations;
        this.sender = sender;
        this.progressLog = progressLog;
        this.forwardRetryConfig = forwardRetryConfig;

        init();
    }

    private void init() {
        // Add forward records for new forward URLs.
        forwardSourceDao.addNewForwardSources(forwardDestinations.getNewForwardDests());

        // Remove forward records for forward URLs that are no longer in use.
        forwardSourceDao.removeOldForwardSources(forwardDestinations.getOldForwardDests());
    }

//    public void createAllForwardRecords() {
//        QueueUtil.consumeAll(() -> sources.getNewSource(0, TimeUnit.MILLISECONDS),
//                this::createForwardRecord);
//    }
//
//    public void createNextForwardRecord() {
//        sources.getNewSource().ifPresent(this::createForwardRecord);
//    }

    public synchronized void createAllForwardSources() {
        BatchUtil.transfer(sources::getNewSources, this::createForwardSources);
    }

    private void createForwardSources(final Batch<RepoSource> batch) {
        // Forward to all remaining places.
        progressLog.increment("SourceForwarder - createForwardRecord");
        forwardSourceDao.createForwardSources(batch, forwardDestinations.getForwardDests());
    }

//    public void forwardAll() {
//        Optional<ForwardSource> optionalForwardAggregate;
//        do {
//            optionalForwardAggregate = forwardSourceDao.getNewForwardSource(0, TimeUnit.MILLISECONDS);
//            optionalForwardAggregate.ifPresent(this::forwardSource);
//        } while (optionalForwardAggregate.isPresent());
//    }
//
//    public void forwardNext() {
//        final Optional<ForwardSource> optional = forwardSourceDao.getNewForwardSource();
//        optional.ifPresent(this::forwardSource);
//    }
//
//    private void forwardSource(final ForwardSource forwardSource) {
//        progressLog.increment("SourceForwarder - forwardSource");
//        forward(forwardSource);
//    }

    public Batch<ForwardSource> getNewForwardSources() {
        return forwardSourceDao.getNewForwardSources();
    }

    public void forwardAll() {
        BatchUtil.transferEach(
                () -> forwardSourceDao.getNewForwardSources(0, TimeUnit.SECONDS),
                this::forward);
    }

    public Batch<ForwardSource> getRetryForwardSources() {
        return forwardSourceDao.getRetryForwardSources();
    }

    public void forwardRetry(final ForwardSource forwardSource) {
        progressLog.increment("SourceForwarder - forwardRetry");

        final long retryFrequency = forwardRetryConfig.getRetryFrequency().toMillis();
        final long updateTimeMs = forwardSource.getUpdateTimeMs();

        // The current item will be the oldest so sleep if it isn't at least as old as the min retry frequency.
        final long delay = retryFrequency - (System.currentTimeMillis() - updateTimeMs);
        if (delay > 0) {
            // Sleep at least as long as the retry frequency.
            ThreadUtil.sleep(delay);
        }

        final long lastTryTimeMs = forwardSource.getLastTryTimeMs();
        final long nextExecution = lastTryTimeMs +
                (retryFrequency *
                        forwardSource.getTries() *
                        forwardSource.getTries());

        if (nextExecution < System.currentTimeMillis()) {
            forward(forwardSource);

        } else {
            // We are not ready to try forwarding this item again yet so put it to the end of the queue.
            final ForwardSource updatedForwardSource = forwardSource
                    .copy()
                    .updateTimeMs(System.currentTimeMillis())
                    .build();
            forwardSourceDao.update(updatedForwardSource);
        }
    }

    public void forward(final ForwardSource forwardSource) {
        final RepoSource source = forwardSource.getSource();
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final FeedKey feedKey = feedDao.getKey(source.feedId());
        final long thisPostId = proxyForwardId.incrementAndGet();
        final String info = thisPostId + " " + feedKey.feed() + " - " + feedKey.type();
        LOGGER.debug(() -> "processFeedFiles() - proxyForwardId " + info);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
        attributeMap.put(StandardHeaderArguments.FEED, feedKey.feed());
        if (feedKey.type() != null) {
            attributeMap.put(StandardHeaderArguments.TYPE, feedKey.type());
        }
        if (LOGGER.isDebugEnabled()) {
            attributeMap.put(CIKeys.PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final StreamHandlers streamHandlers;
        // If we have reached the max tried limit then send the data to the failure destination for this forwarder.
        if (forwardSource.getTries() >= forwardRetryConfig.getMaxTries()) {
            attributeMap.put(CIKeys.FORWARD_ERROR, forwardSource.getError());
            streamHandlers = failureDestinations.getProvider(forwardSource.getForwardDest().getName());
        } else {
            streamHandlers = forwarderDestinations.getProvider(forwardSource.getForwardDest().getName());
        }

        // Start the POST
        try {
            streamHandlers.handle(feedKey.feed(), feedKey.type(), attributeMap, handler -> {
                sender.sendDataToHandler(source, handler);
                success.set(true);
                progressLog.increment("SourceForwarder - forward");
            });

        } catch (final RuntimeException ex) {
            success.set(false);
            error.set(ex.getMessage());
            LOGGER.warn(() -> "Failed to send to feed " +
                    feedKey.feed() +
                    " (" +
                    ex.getMessage() +
                    ")");
            LOGGER.debug(() -> "Failed to send to feed " + info, ex);
        }

        // Record that we sent the data or if there was no data to send.
        final long now = System.currentTimeMillis();
        final ForwardSource updatedForwardSource = forwardSource
                .copy()
                .updateTimeMs(now)
                .success(success.get())
                .error(error.get())
                .tries(forwardSource.getTries() + 1)
                .lastTryTimeMs(now)
                .build();
        forwardSourceDao.update(updatedForwardSource);
    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    public void clear() {
        forwardSourceDao.clear();
    }

    public void flush() {
        forwardSourceDao.flush();
    }
}
