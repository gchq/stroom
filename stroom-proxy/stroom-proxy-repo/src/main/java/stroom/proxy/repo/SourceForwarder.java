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
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.receive.common.StreamHandlers;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.HostNameUtil;

import java.nio.file.Path;
import java.util.Optional;
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

    private final RepoSources sources;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardUrls forwardUrls;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwarderDestinations forwarderDestinations;
    private final Path repoDir;
    private final ProgressLog progressLog;
    private final Sender sender;

    private volatile String hostName = null;

    @Inject
    SourceForwarder(final RepoSources sources,
                    final ForwardSourceDao forwardSourceDao,
                    final ForwardUrls forwardUrls,
                    final ForwarderDestinations forwarderDestinations,
                    final RepoDirProvider repoDirProvider,
                    final ProgressLog progressLog,
                    final Sender sender) {

        this.sources = sources;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardUrls = forwardUrls;
        this.progressLog = progressLog;
        this.sender = sender;

        this.forwarderDestinations = forwarderDestinations;
        this.repoDir = repoDirProvider.get();

        init();
    }

    public void init() {
        // Add forward records for new forward URLs.
        forwardSourceDao.addNewForwardSources(forwardUrls.getNewForwardUrls());

        // Remove forward records for forward URLs that are no longer in use.
        forwardSourceDao.removeOldForwardSources(forwardUrls.getOldForwardUrls());
    }

    public void createAllForwardRecords() {
        QueueUtil.consumeAll(() -> sources.getNewSource(0, TimeUnit.MILLISECONDS),
                this::createForwardRecord);
    }

    public void createNextForwardRecord() {
        sources.getNewSource().ifPresent(this::createForwardRecord);
    }

    private void createForwardRecord(final RepoSource source) {
        // Forward to all remaining places.
        progressLog.increment("SourceForwarder - createForwardRecord");
        forwardSourceDao.createForwardSources(source.getId(), forwardUrls.getForwardUrls());
    }

    public void forwardAll() {
        Optional<ForwardSource> optionalForwardAggregate;
        do {
            optionalForwardAggregate = forwardSourceDao.getNewForwardSource(0, TimeUnit.MILLISECONDS);
            optionalForwardAggregate.ifPresent(this::forwardSource);
        } while (optionalForwardAggregate.isPresent());
    }

    public void forwardNext() {
        final Optional<ForwardSource> optional = forwardSourceDao.getNewForwardSource();
        optional.ifPresent(this::forwardSource);
    }

    private void forwardSource(final ForwardSource forwardSource) {
        progressLog.increment("SourceForwarder - forwardSource");
        forward(forwardSource);
    }

    public void forwardRetry(final long oldest) {
        final Optional<ForwardSource> optional = forwardSourceDao.getRetryForwardSource();
        optional.ifPresent(forwardSource -> {
            progressLog.increment("SourceForwarder - forwardRetry");

            final long updateTime = forwardSource.getUpdateTimeMs();
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
            forward(forwardSource);
        });
    }

    private void forward(final ForwardSource forwardSource) {
        final RepoSource source = forwardSource.getSource();
        final String forwardUrl = forwardSource.getForwardUrl().getUrl();
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

        // Start the POST
        try {
            streamHandlers.handle(source.getFeedName(), source.getTypeName(), attributeMap, handler -> {
                sender.sendDataToHandler(source, handler);
                success.set(true);
                progressLog.increment("SourceForwarder - forward");
            });

        } catch (final RuntimeException ex) {
            error.set(ex.getMessage());
            LOGGER.warn(() -> "processFeedFiles() - Failed to send to feed " + source.getFeedName() + " ( " + ex + ")");
            LOGGER.debug(() -> "processFeedFiles() - Debug trace " + info, ex);
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
}
