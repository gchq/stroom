package stroom.proxy.app.event;

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
import stroom.util.logging.Metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

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
public class EventStore implements EventConsumer, RemovalListener<FeedKey, EventAppender> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStore.class);

    private final ReceiverFactory receiverFactory;
    private final Path dir;
    private final Provider<EventStoreConfig> eventStoreConfigProvider;
    private final Cache<FeedKey, EventAppender> openAppenders;
    private final Map<FeedKey, EventAppender> stores;
    private final EventSerialiser eventSerialiser;
    private final LinkedBlockingQueue<Path> forwardQueue;

    @Inject
    public EventStore(final ReceiverFactory receiverFactory,
                      final Provider<EventStoreConfig> eventStoreConfigProvider,
                      final DataDirProvider dataDirProvider,
                      final FileStores fileStores) {
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
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        cacheBuilder.maximumSize(eventStoreConfig.getMaxOpenFiles());
        cacheBuilder.removalListener(this);
        this.openAppenders = cacheBuilder.build();
        this.stores = new ConcurrentHashMap<>();
        eventSerialiser = new EventSerialiser();

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
            Metrics.measure("ProxyRequestHandler - handle", () -> {
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

    @Override
    public void onRemoval(@Nullable final FeedKey feedKey,
                          @Nullable final EventAppender appender,
                          @NonNull final RemovalCause cause) {
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
            final String feed = attributeMap.get("Feed");
            final String type = attributeMap.get("type");
            final FeedKey feedKey = new FeedKey(feed, type);

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
                    openAppenders.invalidate(k);
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
                openAppenders.put(k, eventAppender);

            } else {
                // Keep the existing appender open by keeping its cache entry fresh.
                openAppenders.getIfPresent(feedKey);
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
