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
import stroom.proxy.repo.dao.ForwardSourceDao;
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
public class SourceForwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceForwarder.class);
    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final FeedDao feedDao;
    private final RepoSources sources;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardDestinations forwardDestinations;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final Sender sender;
    private final ProgressLog progressLog;

    private volatile String hostName = null;

    @Inject
    SourceForwarder(final FeedDao feedDao,
                    final RepoSources sources,
                    final ForwardSourceDao forwardSourceDao,
                    final ForwardDestinations forwardDestinations,
                    final ForwarderDestinations forwarderDestinations,
                    final Sender sender,
                    final ProgressLog progressLog) {

        this.feedDao = feedDao;
        this.sources = sources;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardDestinations = forwardDestinations;

        this.forwarderDestinations = forwarderDestinations;
        this.sender = sender;
        this.progressLog = progressLog;

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

    public void forwardRetry(final ForwardSource forwardSource,
                             final long retryFrequency) {
        final long oldest = System.currentTimeMillis() - retryFrequency;
        progressLog.increment("AggregateForwarder - forwardRetry");

        final long updateTime = forwardSource.getUpdateTimeMs();
        final long delay = updateTime - oldest;
        // Wait until the item is old enough before sending.
        if (delay > 0) {
            ThreadUtil.sleep(delay);
        }
        forward(forwardSource);
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
            attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
        }

        final StreamHandlers streamHandlers =
                forwarderDestinations.getProvider(forwardSource.getForwardDest().getName());

        // Start the POST
        try {
            streamHandlers.handle(feedKey.feed(), feedKey.type(), attributeMap, handler -> {
                sender.sendDataToHandler(source, handler);
                success.set(true);
                progressLog.increment("SourceForwarder - forward");
            });

        } catch (final RuntimeException ex) {
            error.set(ex.getMessage());
            LOGGER.warn(() -> "Failed to send to feed " +
                    feedKey.feed() +
                    " (" +
                    ex.getMessage() +
                    ")");
            LOGGER.debug(() -> "Failed to send to feed " + info, ex);
        }

        // Record that we sent the data or if there was no data to send.
        final ForwardSource updatedForwardAggregate = forwardSource
                .copy()
                .updateTimeMs(System.currentTimeMillis())
                .success(success.get())
                .error(error.get())
                .tries(forwardSource.getTries() + 1)
                .build();
        forwardSourceDao.update(updatedForwardAggregate);
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
