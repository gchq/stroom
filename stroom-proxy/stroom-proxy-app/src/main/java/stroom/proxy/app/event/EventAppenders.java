package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.event.EventAppender.Appender;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EventAppenders implements EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventAppenders.class);

    private final Path dir;
    private final SequentialFileStore sequentialFileStore;
    private final AtomicLong fileNum;

    private final ConcurrentHashMap<FeedKey, EventAppender> map = new ConcurrentHashMap<>();

    @Inject
    public EventAppenders(final RepoDirProvider repoDirProvider,
                          final SequentialFileStore sequentialFileStore) {
        this.dir = repoDirProvider.get().resolve("events");

        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        this.sequentialFileStore = sequentialFileStore;
        fileNum = new AtomicLong();

        // Transfer old files to the store.
        try (final Stream<Path> stream = Files.list(dir)) {
            stream.forEach(file -> {
                try {
                    if (file.getFileName().toString().endsWith(".meta") && Files.isRegularFile(file)) {
                        final String metaFileName = file.getFileName().toString();
                        final int index = metaFileName.lastIndexOf(".");
                        final Path datFile = file.getParent().resolve(metaFileName.substring(0, index) + ".dat");

                        if (Files.isRegularFile(datFile)) {
                            final Appender appender = new Appender(file, datFile);
                            appender.transfer(sequentialFileStore);
                        }

                        Files.deleteIfExists(file);
                        Files.deleteIfExists(datFile);
                    }
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void consume(final AttributeMap attributeMap, final Consumer<OutputStream> consumer) {
        final String feed = attributeMap.get("Feed");
        final String type = attributeMap.get("type");
        final FeedKey feedKey = new FeedKey(feed, type);

        map.compute(feedKey, (k, v) -> {
            if (v == null) {
                v = new EventAppender(dir, fileNum, sequentialFileStore);
            } else {
                // Transfer data if it is time to do so.
                v.transfer();
            }

            v.consume(attributeMap, consumer);
            return v;
        });
    }

    public void transferAll() {
        map.values().forEach(EventAppender::transfer);
    }
}
