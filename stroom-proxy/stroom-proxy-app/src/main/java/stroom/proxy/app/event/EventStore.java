/*
 * Copyright 2022 Crown Copyright
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

package stroom.proxy.app.event;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.repo.store.FileStores;
import stroom.security.api.CommonSecurityContext;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.Timer;
import io.dropwizard.lifecycle.Managed;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Singleton
public class EventStore implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStore.class);
    private static final String CACHE_NAME = "Event Store Open Appenders";
    public static final String EVENT_STORE_NAME_PART = "eventStore";

    private final ReceiverFactory receiverFactory;
    private final CommonSecurityContext securityContext;
    private final Path dir;
    private final Provider<EventStoreConfig> eventStoreConfigProvider;
    private final StroomCache<FeedKey, EventAppender> openAppendersCache;
    private final Map<FeedKey, EventAppender> stores;
    private final EventSerialiser eventSerialiser;
    private final LinkedBlockingQueue<Path> forwardQueue;
    private final Timer handleTimer;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    @Inject
    public EventStore(final ReceiverFactory receiverFactory,
                      final CommonSecurityContext securityContext,
                      final Provider<EventStoreConfig> eventStoreConfigProvider,
                      final DataDirProvider dataDirProvider,
                      final FileStores fileStores,
                      final CacheManager cacheManager,
                      final Metrics metrics) {
        this.eventStoreConfigProvider = eventStoreConfigProvider;
        final EventStoreConfig eventStoreConfig = eventStoreConfigProvider.get();
        this.forwardQueue = new LinkedBlockingQueue<>(eventStoreConfig.getForwardQueueSize());
        final Path dataDir = dataDirProvider.get();

        // Create the data directory
        ensureDirExists(dataDir);

        // Create the event directory.
        dir = dataDir.resolve("event");
        ensureDirExists(dir);
        fileStores.add(0, "Event Store", dir);

        this.receiverFactory = receiverFactory;
        this.securityContext = securityContext;

        this.openAppendersCache = cacheManager.create(
                CACHE_NAME,
                () -> eventStoreConfigProvider.get().getOpenFilesCache(),
                this::onCacheRemoval);

        this.stores = new ConcurrentHashMap<>();
        this.eventSerialiser = new EventSerialiser();

        this.handleTimer = metrics.registrationBuilder(getClass())
                .addNamePart(EVENT_STORE_NAME_PART)
                .addNamePart(Metrics.HANDLE)
                .timer()
                .createAndRegister();

        forwardOldFiles();
    }

    private void checkState() {
        if (shutdown.get()) {
            throw new IllegalStateException("Event Store has been shut down");
        }
    }

    private void ensureDirExists(final Path path) {
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void forwardOldFiles() {
        try (final Stream<Path> stream = Files.list(dir)) {
            stream.forEach(this::forward);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public void tryRoll() {
        stores.keySet().forEach(feedKey -> {
            LOGGER.debug("Try rolling: {}", feedKey);
            final AtomicReference<Path> rolledFile = new AtomicReference<>();

            stores.compute(feedKey, (k, v) -> {
                EventAppender eventAppender = v;
                if (eventAppender != null && eventAppender.shouldRoll(0)) {
                    rolledFile.set(eventAppender.closeAndGetFile());
                    eventAppender = null;
                }
                return eventAppender;
            });

            enqueueForForwarding(rolledFile.get());
        });
    }

    public void roll() {
        stores.keySet().forEach(feedKey -> {
            LOGGER.debug("Rolling: {}", feedKey);
            final AtomicReference<Path> rolledFile = new AtomicReference<>();

            stores.compute(feedKey, (k, v) -> {
                if (v != null) {
                    rolledFile.set(v.closeAndGetFile());
                }
                return null;
            });

            enqueueForForwarding(rolledFile.get());
        });
    }

    /**
     * Take the next rolled file from the forward queue and forward it, blocking until
     * one is available.
     * <p>
     * This handles a single file and returns, so it must be driven by a
     * {@code ParallelExecutor}, which re-invokes its runnable in a loop. It was
     * previously an unbounded {@code while} loop registered as a <em>frequency</em>
     * executor, which meant the first invocation never returned and the configured
     * frequency was meaningless.
     * </p>
     */
    public void forwardNext() {
        try {
            final Path file = forwardQueue.take();
            try {
                forward(file);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedInterruptedException.create(e);
        }
    }

    private void forward(final Path file) {
        LOGGER.debug("Forwarding: {}", file);
        if (Files.isRegularFile(file)) {
            final FeedKey feedKey = EventStoreFile.getFeedKey(file);

            final AttributeMap attributeMap = new AttributeMap();
            if (feedKey.feed() != null) {
                attributeMap.put(StandardHeaderArguments.FEED, feedKey.feed());
            }
            if (feedKey.type() != null) {
                attributeMap.put(StandardHeaderArguments.TYPE, feedKey.type());
            }

            // Consume the data
            handleTimer.time(() -> {
                final AtomicBoolean success = new AtomicBoolean();
                try (final BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(file))) {
                    // The request that produced these events was authenticated and filtered long ago,
                    // under ReceiveDataHelper's elevation. This runs later, on the forwarding thread
                    // (and at startup for files left behind), so no user is in scope - yet receive()
                    // filters again and the feed status lookup needs an identity. Elevate for the same
                    // reason the datafeed and dir-scanner entry points do.
                    securityContext.asProcessingUser(() ->
                            receiverFactory
                                    .get(attributeMap)
                                    .receive(Instant.now(), attributeMap, "event-store", () -> inputStream));
                    success.set(true);
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }

                try {
                    if (success.get()) {
                        Files.delete(file);
                    }
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    public void onCacheRemoval(@Nullable final FeedKey feedKey,
                               @Nullable final EventAppender appender) {
        try {
            if (appender != null) {
                appender.close();
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    public void consume(final AttributeMap attributeMap,
                        final UniqueId receiptId,
                        final String data) {
        try {
            checkState();
            final FeedKey feedKey = FeedKey.from(attributeMap);
            final String string = eventSerialiser.serialise(
                    receiptId,
                    feedKey,
                    attributeMap,
                    data) + "\n";
            final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            put(feedKey, bytes);

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    private void put(final FeedKey feedKey,
                     final byte[] bytes) {
        // Captured inside compute() and enqueued after it returns - see enqueueForForwarding.
        final AtomicReference<Path> rolledFile = new AtomicReference<>();

        stores.compute(feedKey, (k, v) -> {
            EventAppender eventAppender = v;

            // Roll the current appender if we have one if it is time to roll.
            if (eventAppender != null && eventAppender.shouldRoll(bytes.length)) {
                rolledFile.set(eventAppender.closeAndGetFile());
                // Invalidate the cache item that keeps the appender open.
                openAppendersCache.invalidate(k);
                eventAppender = null;
            }

            if (eventAppender == null) {
                // Create a new appender and add it to the cache of open items.
                Instant now = null;
                Path file = null;
                boolean success = false;

                while (!success) {
                    now = Instant.now();
                    file = EventStoreFile.createNew(dir, k, now);
                    // Ensure file doesn't already exist.
                    if (Files.isRegularFile(file)) {
                        LOGGER.debug("File already exists: {}", file);
                        ThreadUtil.sleep(1);
                    } else {
                        success = true;
                    }
                }

                // Config is fixed until next roll
                eventAppender = new EventAppender(file, now, eventStoreConfigProvider.get());
                openAppendersCache.put(k, eventAppender);

            } else if (openAppendersCache.getIfPresent(feedKey).isEmpty()) {
                // Keeping the entry fresh is normally all that is needed, but if it has
                // been evicted the appender is still in use - write() reopens the file
                // lazily - so re-register it. Without this the open handle stops being
                // accounted for by the cache and the open-file limit is not honoured
                // for this feed until it next rolls.
                openAppendersCache.put(k, eventAppender);
            }

            try {
                // Write to the appender.
                eventAppender.write(bytes);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(e);
            }

            return eventAppender;
        });

        enqueueForForwarding(rolledFile.get());
    }

    /**
     * Hand a rolled file to the forward queue.
     * <p>
     * Deliberately called <em>outside</em> {@code stores.compute()}. The forward queue
     * is bounded, so {@link LinkedBlockingQueue#put} blocks once it is full - which is
     * the intended backpressure, but doing it inside {@code compute()} would block
     * while holding that bin's lock and stall unrelated feeds whose {@link FeedKey}
     * hashes to the same bin. {@code ConcurrentHashMap} explicitly requires the
     * remapping function to be short and non-blocking.
     * </p>
     * <p>
     * The file is closed and complete by this point, so enqueuing it slightly later
     * only delays forwarding. If the thread is interrupted before it is enqueued the
     * file is still on disk and is picked up by {@code forwardOldFiles()} at startup.
     * </p>
     */
    private void enqueueForForwarding(@Nullable final Path file) {
        if (file == null) {
            return;
        }
        try {
            forwardQueue.put(file);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public void stop() throws Exception {
        if (shutdown.compareAndSet(false, true)) {
            stores.values()
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(eventAppender -> {
                        try {
                            eventAppender.close();
                        } catch (final IOException e) {
                            LOGGER.error("Error closing eventAppender {}", eventAppender, e);
                        }
                    });
        }
    }
}
