/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.Timer;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Singleton
public class EventStore implements EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStore.class);
    private static final String CACHE_NAME = "Event Store Open Appenders";
    public static final String EVENT_STORE_NAME_PART = "eventStore";

    private final ReceiverFactory receiverFactory;
    private final Path dir;
    private final Provider<EventStoreConfig> eventStoreConfigProvider;
    private final StroomCache<FeedKey, EventAppender> openAppendersCache;
    private final Map<FeedKey, EventAppender> stores;
    private final EventSerialiser eventSerialiser;
    private final LinkedBlockingQueue<Path> forwardQueue;
    private final Timer handleTimer;

    @Inject
    public EventStore(final ReceiverFactory receiverFactory,
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
            LOGGER.debug(() -> "Try rolling: " + feedKey.toString());
            stores.compute(feedKey, (k, v) -> {
                EventAppender eventAppender = v;
                if (eventAppender != null) {
                    if (eventAppender.shouldRoll(0)) {
                        try {
                            forwardQueue.put(eventAppender.closeAndGetFile());
                            eventAppender = null;
                        } catch (final InterruptedException e) {
                            throw UncheckedInterruptedException.create(e);
                        }
                    }
                }
                return eventAppender;
            });
        });
    }

    public void roll() {
        stores.keySet().forEach(feedKey -> {
            LOGGER.debug(() -> "Rolling: " + feedKey.toString());
            stores.compute(feedKey, (k, v) -> {
                if (v != null) {
                    try {
                        forwardQueue.put(v.closeAndGetFile());
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }
                }
                return null;
            });
        });
    }

    public void forwardAll() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Path file = forwardQueue.take();
                    forward(file);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private void forward(final Path file) {
        LOGGER.debug(() -> "Forwarding: " + file);
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
                    receiverFactory
                            .get(attributeMap)
                            .receive(Instant.now(), attributeMap, "event-store", () -> inputStream);
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

    @Override
    public void consume(final AttributeMap attributeMap,
                        final UniqueId receiptId,
                        final String data) {
        try {
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
        stores.compute(feedKey, (k, v) -> {
            EventAppender eventAppender = v;

            // Roll the current appender if we have one if it is time to roll.
            if (eventAppender != null && eventAppender.shouldRoll(bytes.length)) {
                try {
                    // Add the appender to the forward queue.
                    forwardQueue.put(eventAppender.closeAndGetFile());
                    // Invalidate the cache item that keeps the appender open.
                    openAppendersCache.invalidate(k);
                    eventAppender = null;
                } catch (final InterruptedException e) {
                    throw UncheckedInterruptedException.create(e);
                }
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
                        LOGGER.debug("File already exists: " + file);
                        ThreadUtil.sleep(1);
                    } else {
                        success = true;
                    }
                }

                // Config is fixed until next roll
                eventAppender = new EventAppender(file, now, eventStoreConfigProvider.get());
                openAppendersCache.put(k, eventAppender);

            } else {
                // Keep the existing appender open by keeping its cache entry fresh.
                openAppendersCache.getIfPresent(feedKey);
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
    }
}
