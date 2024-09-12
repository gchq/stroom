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
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;
import stroom.receive.common.StreamHandlers;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.string.CIKeys;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class AggregateForwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateForwarder.class);

    private final FeedDao feedDao;
    private final SourceItemDao sourceItemDao;
    private final Aggregator aggregator;
    private final ForwardAggregateDao forwardAggregateDao;
    private final ForwardDestinations forwardDestinations;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final FailureDestinations failureDestinations;
    private final Sender sender;
    private final ProgressLog progressLog;
    private final Provider<ForwardRetryConfig> forwardRetryConfigProvider;

    private volatile String hostName = null;

    @Inject
    AggregateForwarder(final FeedDao feedDao,
                       final SourceItemDao sourceItemDao,
                       final Aggregator aggregator,
                       final ForwardAggregateDao forwardAggregateDao,
                       final ForwardDestinations forwardDestinations,
                       final ForwarderDestinations forwarderDestinations,
                       final FailureDestinations failureDestinations,
                       final Sender sender,
                       final ProgressLog progressLog,
                       final Provider<ForwardRetryConfig> forwardRetryConfigProvider) {
        this.feedDao = feedDao;
        this.sourceItemDao = sourceItemDao;
        this.aggregator = aggregator;
        this.forwardAggregateDao = forwardAggregateDao;
        this.forwardDestinations = forwardDestinations;
        this.failureDestinations = failureDestinations;

        this.forwarderDestinations = forwarderDestinations;
        this.sender = sender;
        this.progressLog = progressLog;
        this.forwardRetryConfigProvider = forwardRetryConfigProvider;

        init();
    }

    private void init() {
        // Add forward records for new forward URLs.
        forwardAggregateDao.addNewForwardAggregates(forwardDestinations.getNewForwardDests());

        // Remove forward records for forward URLs that are no longer in use.
        forwardAggregateDao.removeOldForwardAggregates(forwardDestinations.getOldForwardDests());
    }

//    public void createAllForwardRecords() {
//        final Batch<Aggregate> aggregates = aggregator.getCompleteAggregates(0, TimeUnit.MILLISECONDS);
//        createForwardRecord(aggregates);
//    }

    public synchronized void createAllForwardAggregates() {
        BatchUtil.transfer(aggregator::getCompleteAggregates, this::createForwardAggregates);
    }

    private void createForwardAggregates(final Batch<Aggregate> batch) {
        // Forward to all remaining places.
        progressLog.increment("AggregateForwarder - createForwardRecord");
        forwardAggregateDao.createForwardAggregates(batch, forwardDestinations.getForwardDests());
    }

    public void forwardAll() {
        BatchUtil.transferEach(
                () -> forwardAggregateDao.getNewForwardAggregates(0, TimeUnit.SECONDS),
                this::forward);

//        QueueUtil.consumeAll(() -> forwardAggregateDao.getNewForwardAggregate(0, TimeUnit.MILLISECONDS),
//                this::forwardAggregate);
    }

//    public void forwardNext() {
//        forwardAggregateDao.getNewForwardAggregate().ifPresent(this::forwardAggregate);
//    }
//
//    private void forwardAggregate(final ForwardAggregate forwardAggregate) {
//        progressLog.increment("AggregateForwarder - forwardAggregate");
//        forward(forwardAggregate);
//    }

    public Batch<ForwardAggregate> getNewForwardAggregates() {
        return forwardAggregateDao.getNewForwardAggregates();
    }

    public Batch<ForwardAggregate> getRetryForwardAggregates() {
        return forwardAggregateDao.getRetryForwardAggregate();
    }

    public void forwardRetry(final ForwardAggregate forwardAggregate) {
        progressLog.increment("AggregateForwarder - forwardRetry");

        final long retryFrequency = forwardRetryConfigProvider.get().getRetryFrequency().toMillis();
        final long updateTimeMs = forwardAggregate.getUpdateTimeMs();

        // The current item will be the oldest so sleep if it isn't at least as old as the min retry frequency.
        final long delay = retryFrequency - (System.currentTimeMillis() - updateTimeMs);
        if (delay > 0) {
            // Sleep at least as long as the retry frequency.
            ThreadUtil.sleep(delay);
        }

        final long lastTryTimeMs = forwardAggregate.getLastTryTimeMs();
        final long nextExecution = lastTryTimeMs +
                (retryFrequency *
                        forwardAggregate.getTries() *
                        forwardAggregate.getTries());

        if (nextExecution < System.currentTimeMillis()) {
            forward(forwardAggregate);

        } else {
            // We are not ready to try forwarding this item again yet so put it to the end of the queue.
            final ForwardAggregate updatedForwardAggregate = forwardAggregate
                    .copy()
                    .updateTimeMs(System.currentTimeMillis())
                    .build();
            forwardAggregateDao.update(updatedForwardAggregate);
        }
    }

    public void forward(final ForwardAggregate forwardAggregate) {
        final Aggregate aggregate = forwardAggregate.getAggregate();
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final List<SourceItems> items = sourceItemDao.fetchSourceItemsByAggregateId(aggregate.id());
        if (!items.isEmpty()) {
            final FeedKey feedKey = feedDao.getKey(aggregate.feedId());
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
            if (forwardAggregate.getTries() >= forwardRetryConfigProvider.get().getMaxTries()) {
                attributeMap.put(CIKeys.FORWARD_ERROR, forwardAggregate.getError());
                streamHandlers = failureDestinations.getProvider(forwardAggregate.getForwardDest().getName());
            } else {
                streamHandlers = forwarderDestinations.getProvider(forwardAggregate.getForwardDest().getName());
            }

            // Start the POST
            try {
                streamHandlers.handle(feedKey.feed(), feedKey.type(), attributeMap, handler -> {
                    sender.sendDataToHandler(attributeMap, items, handler);
                    success.set(true);
                    progressLog.increment("AggregateForwarder - forward");
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
        } else {
            success.set(true);
            progressLog.increment("AggregateForwarder - forward");
        }

        // Record that we sent the data or if there was no data to send.
        final long now = System.currentTimeMillis();
        final ForwardAggregate updatedForwardAggregate = forwardAggregate
                .copy()
                .updateTimeMs(now)
                .success(success.get())
                .error(error.get())
                .tries(forwardAggregate.getTries() + 1)
                .lastTryTimeMs(now)
                .build();
        forwardAggregateDao.update(updatedForwardAggregate);
    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    public void clear() {
        forwardAggregateDao.clear();
    }

    public void flush() {
        forwardAggregateDao.flush();
    }
}
