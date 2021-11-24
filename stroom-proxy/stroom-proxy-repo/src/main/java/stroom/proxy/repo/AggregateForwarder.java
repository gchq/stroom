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
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.receive.common.StreamHandlers;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final Aggregator aggregator;
    private final ForwardAggregateDao forwardAggregateDao;
    private final ForwardUrls forwardUrls;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final Sender sender;
    private final ProgressLog progressLog;

    private volatile String hostName = null;

    @Inject
    AggregateForwarder(final Aggregator aggregator,
                       final ForwardAggregateDao forwardAggregateDao,
                       final ForwardUrls forwardUrls,
                       final ForwarderDestinations forwarderDestinations,
                       final Sender sender,
                       final ProgressLog progressLog) {

        this.aggregator = aggregator;
        this.forwardAggregateDao = forwardAggregateDao;
        this.forwardUrls = forwardUrls;

        this.forwarderDestinations = forwarderDestinations;
        this.sender = sender;
        this.progressLog = progressLog;

        init();
    }

    private void init() {
        // Add forward records for new forward URLs.
        forwardAggregateDao.addNewForwardAggregates(forwardUrls.getNewForwardUrls());

        // Remove forward records for forward URLs that are no longer in use.
        forwardAggregateDao.removeOldForwardAggregates(forwardUrls.getOldForwardUrls());
    }

    public void createAllForwardRecords() {
        QueueUtil.consumeAll(() -> aggregator.getCompleteAggregate(0, TimeUnit.MILLISECONDS),
                this::createForwardRecord);
    }

    public void createNextForwardRecord() {
        aggregator.getCompleteAggregate().ifPresent(this::createForwardRecord);
    }

    private void createForwardRecord(final Aggregate aggregate) {
        // Forward to all remaining places.
        progressLog.increment("AggregateForwarder - createForwardRecord");
        forwardAggregateDao.createForwardAggregates(aggregate.getId(), forwardUrls.getForwardUrls());
    }

    public void forwardAll() {
        QueueUtil.consumeAll(() -> forwardAggregateDao.getNewForwardAggregate(0, TimeUnit.MILLISECONDS),
                this::forwardAggregate);
    }

    public void forwardNext() {
        forwardAggregateDao.getNewForwardAggregate().ifPresent(this::forwardAggregate);
    }

    private void forwardAggregate(final ForwardAggregate forwardAggregate) {
        progressLog.increment("AggregateForwarder - forwardAggregate");
        forward(forwardAggregate);
    }

    public void forwardRetry(final long oldest) {
        final Optional<ForwardAggregate> optionalForwardAggregate = forwardAggregateDao.getRetryForwardAggregate();
        optionalForwardAggregate.ifPresent(forwardAggregate -> {
            progressLog.increment("AggregateForwarder - forwardRetry");

            final long updateTime = forwardAggregate.getUpdateTimeMs();
            final long delay = updateTime - oldest;
            // Wait until the item is old enough before sending.
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (final InterruptedException e) {
                    // Continue to interrupt.
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            forward(forwardAggregate);
        });
    }

    void forward(final ForwardAggregate forwardAggregate) {
        final Aggregate aggregate = forwardAggregate.getAggregate();
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final Map<RepoSource, List<RepoSourceItem>> items = aggregator.getItems(aggregate.getId());
        if (items.size() > 0) {
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

            final StreamHandlers streamHandlers =
                    forwarderDestinations.getProvider(forwardAggregate.getForwardUrl().getUrl());

            // Start the POST
            try {
                streamHandlers.handle(aggregate.getFeedName(), aggregate.getTypeName(), attributeMap, handler -> {
                    sender.sendDataToHandler(items, handler);
                    success.set(true);
                    progressLog.increment("AggregateForwarder - forward");
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
}
