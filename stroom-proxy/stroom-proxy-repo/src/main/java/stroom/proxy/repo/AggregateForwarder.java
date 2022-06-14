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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AggregateForwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateForwarder.class);
    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final FeedDao feedDao;
    private final SourceItemDao sourceItemDao;
    private final Aggregator aggregator;
    private final ForwardAggregateDao forwardAggregateDao;
    private final ForwardDestinations forwardDestinations;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final Sender sender;
    private final ProgressLog progressLog;

    private volatile String hostName = null;

    @Inject
    AggregateForwarder(final FeedDao feedDao,
                       final SourceItemDao sourceItemDao,
                       final Aggregator aggregator,
                       final ForwardAggregateDao forwardAggregateDao,
                       final ForwardDestinations forwardDestinations,
                       final ForwarderDestinations forwarderDestinations,
                       final Sender sender,
                       final ProgressLog progressLog) {
        this.feedDao = feedDao;
        this.sourceItemDao = sourceItemDao;
        this.aggregator = aggregator;
        this.forwardAggregateDao = forwardAggregateDao;
        this.forwardDestinations = forwardDestinations;

        this.forwarderDestinations = forwarderDestinations;
        this.sender = sender;
        this.progressLog = progressLog;

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

    public void forwardRetry(final ForwardAggregate forwardAggregate,
                             final long retryFrequency) {
        final long oldest = System.currentTimeMillis() - retryFrequency;
        progressLog.increment("AggregateForwarder - forwardRetry");

        final long updateTime = forwardAggregate.getUpdateTimeMs();
        final long delay = updateTime - oldest;
        // Wait until the item is old enough before sending.
        if (delay > 0) {
            ThreadUtil.sleep(delay);
        }
        forward(forwardAggregate);
    }

    public void forward(final ForwardAggregate forwardAggregate) {
        final Aggregate aggregate = forwardAggregate.getAggregate();
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final Items items = sourceItemDao.fetchSourceItemsByAggregateId(aggregate.id());
        if (items.map().size() > 0) {
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
                attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
            }

            final StreamHandlers streamHandlers =
                    forwarderDestinations.getProvider(forwardAggregate.getForwardDest().getName());

            // Start the POST
            try {
                streamHandlers.handle(feedKey.feed(), feedKey.type(), attributeMap, handler -> {
                    sender.sendDataToHandler(items, handler);
                    success.set(true);
                    progressLog.increment("AggregateForwarder - forward");
                });
            } catch (final RuntimeException ex) {
                error.set(ex.getMessage());
                LOGGER.warn(() -> "Failed to send to feed " +
                        feedKey.feed() +
                        " ( " +
                        ex.getMessage() +
                        ")");
                LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
            }
        } else {
            success.set(true);
            progressLog.increment("AggregateForwarder - forward");
        }

        // Record that we sent the data or if there was no data to send.
        final ForwardAggregate updatedForwardAggregate = forwardAggregate
                .copy()
                .updateTimeMs(System.currentTimeMillis())
                .success(success.get())
                .error(error.get())
                .tries(forwardAggregate.getTries() + 1)
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
