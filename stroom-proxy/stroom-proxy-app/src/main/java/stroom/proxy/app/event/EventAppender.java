package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class EventAppender implements EventConsumer, AutoCloseable {

    private final Path dir;
    private final AtomicLong fileNum;
    private final SequentialFileStore fileStore;
    private Appender currentAppender;

    public EventAppender(final Path dir,
                         final AtomicLong fileNum,
                         final SequentialFileStore fileStore) {
        this.dir = dir;
        this.fileNum = fileNum;
        this.fileStore = fileStore;
    }

    @Override
    public synchronized void consume(final AttributeMap attributeMap,
                                     final Consumer<OutputStream> consumer) {
        if (currentAppender == null) {
            final long num = fileNum.incrementAndGet();
            final String id = StringIdUtil.idToString(num);
            final Path metaFile = dir.resolve(id + ".meta");
            final Path datFile = dir.resolve(id + ".dat");
            currentAppender = new Appender(metaFile, datFile);
        }

        currentAppender.consume(attributeMap, consumer);
    }

    @Override
    public synchronized void close() throws IOException {
        if (currentAppender != null) {
            currentAppender.close();
        }
    }

    public synchronized void transfer() {
        if (currentAppender != null) {
            if (currentAppender.recordCount >= 10000 ||
                    currentAppender.creationTime <= (System.currentTimeMillis() - 60_000)) {
                currentAppender.transfer(fileStore);
                currentAppender = null;
            }
        }
    }

    public static class Appender implements EventConsumer, AutoCloseable {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Appender.class);

        private final Path metaFile;
        private final Path datFile;
        private final long creationTime;
        private SeekableByteChannel channel;
        private boolean closed = true;
        private long recordCount;

        public Appender(final Path metaFile,
                        final Path datFile) {
            this.metaFile = metaFile;
            this.datFile = datFile;
            this.creationTime = System.currentTimeMillis();
        }

        @Override
        public synchronized void consume(final AttributeMap attributeMap,
                                         final Consumer<OutputStream> consumer) {
            try {
                if (closed) {
                    if (!Files.isRegularFile(metaFile)) {
                        try (final OutputStream outputStream =
                                new BufferedOutputStream(Files.newOutputStream(metaFile))) {
                            AttributeMapUtil.write(attributeMap, outputStream);
                        }
                    }
                    if (Files.isRegularFile(datFile)) {
                        channel = Files.newByteChannel(datFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    } else {
                        channel = Files.newByteChannel(datFile,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.CREATE_NEW);
                    }
                    closed = false;
                }

                final OutputStream outputStream = Channels.newOutputStream(channel);
                consumer.accept(outputStream);
                outputStream.flush();

                recordCount++;
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                channel.close();
            }
        }

        public synchronized void transfer(final SequentialFileStore fileStore) {
            try {
                close();

                final AttributeMap attributeMap = new AttributeMap();
                try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(metaFile))) {
                    AttributeMapUtil.read(inputStream, attributeMap);
                }

                try (final Entries entries = fileStore.getEntries(attributeMap)) {
                    try (final OutputStream outputStream = entries.addEntry(metaFile.getFileName().toString())) {
                        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(metaFile))) {
                            StreamUtil.streamToStream(inputStream, outputStream);
                        }
                    }

                    try (final OutputStream outputStream = entries.addEntry(datFile.getFileName().toString())) {
                        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(datFile))) {
                            StreamUtil.streamToStream(inputStream, outputStream);
                        }
                    }
                }

                Files.deleteIfExists(datFile);
                Files.deleteIfExists(metaFile);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        }
    }
}
