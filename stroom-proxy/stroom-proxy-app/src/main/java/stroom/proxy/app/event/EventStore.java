package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.ReceiveStreamHandlers;
import stroom.proxy.repo.RepoDirProvider;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.inject.Inject;

public class EventStore implements EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStore.class);

    private final ReceiveStreamHandlers receiveStreamHandlerProvider;
    private final Path dir;
    private final ProxyConfig proxyConfig;
    private final LoadingCache<FeedKey, EventAppender> cache;
    private final Map<FeedKey, EventAppender> stores;
    private final EventSerialiser eventSerialiser;

    @Inject
    public EventStore(final ReceiveStreamHandlers receiveStreamHandlerProvider,
                      final ProxyConfig proxyConfig,
                      final RepoDirProvider repoDirProvider) {
        final Path repoDir = repoDirProvider.get();

        // Create the root directory
        ensureDirExists(repoDir);

        // Create the event directory.
        dir = repoDir.resolve("event");
        ensureDirExists(dir);

        this.receiveStreamHandlerProvider = receiveStreamHandlerProvider;
        this.proxyConfig = proxyConfig;
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        cacheBuilder.maximumSize(100);
        cacheBuilder.removalListener(this::close);
        this.cache = cacheBuilder.build(this::open);
        this.stores = new ConcurrentHashMap<>();
        eventSerialiser = new EventSerialiser();

        rollOldFiles();
    }

    private boolean ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return true;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return false;
    }

    private void rollOldFiles() {
        try (final Stream<Path> stream = Files.list(dir)) {
            stream.forEach(file -> {
                try {
                    final String fileName = file.getFileName().toString();
                    if (EventStoreFile.isTempFile(fileName)) {
                        final String prefix = EventStoreFile.getPrefix(fileName);
                        final Path rolledFile = dir.resolve(EventStoreFile.createRolledFileName(prefix));
                        Files.move(file, rolledFile, StandardCopyOption.ATOMIC_MOVE);
                        forward(rolledFile);
                    }
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
            });

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public void roll() {
        cache.cleanUp();
        stores.values().forEach(appender -> {
            try {
                final Optional<Path> optionalPath = appender.roll();
                optionalPath.ifPresent(this::forward);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private void forward(final Path file) {
        final String fileName = file.getFileName().toString();
        final String prefix = EventStoreFile.getPrefix(fileName);
        final FeedKey feedKey = FeedKey.decodeKey(prefix);

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
                receiveStreamHandlerProvider.handle(feedKey.feed(), feedKey.type(), attributeMap, handler -> {
                    final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                            attributeMap,
                            handler,
                            new ProgressHandler("Receiving data"));
                    stroomStreamProcessor.processInputStream(inputStream, "");
                    success.set(true);
                });
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

    private EventAppender open(final FeedKey feedKey) {
        return stores.computeIfAbsent(feedKey, k -> new EventAppender(dir, k));
    }

    private void close(final FeedKey feedKey,
                       final EventAppender appender,
                       final RemovalCause removalCause) {
        try {
            appender.close();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void consume(final AttributeMap attributeMap,
                        final String requestUuid,
                        final String data) {
        try {
            final String feed = attributeMap.get("Feed");
            final String type = attributeMap.get("type");
            final FeedKey feedKey = new FeedKey(feed, type);

            final String string = eventSerialiser.serialise(
                    requestUuid,
                    proxyConfig.getProxyId(),
                    feedKey,
                    attributeMap,
                    data);

            final EventAppender appender = cache.get(feedKey);
            appender.write(string);

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }
}
