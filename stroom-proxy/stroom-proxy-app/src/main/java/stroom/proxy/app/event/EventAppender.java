package stroom.proxy.app.event;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;

class EventAppender implements AutoCloseable {

    private final Path dir;
    private final String prefix;
    private final EventStoreConfig eventStoreConfig;
    private OutputStream outputStream;
    private long eventCount = 0;
    private long byteCount = 0;
    private Instant lastRoll;

    public EventAppender(final Path dir,
                         final FeedKey feedKey,
                         final EventStoreConfig eventStoreConfig) {
        this.dir = dir;
        this.eventStoreConfig = eventStoreConfig;
        prefix = feedKey.encodeKey();
        lastRoll = Instant.now();
    }

    public synchronized Optional<Path> write(final byte[] bytes) throws IOException {
        final Optional<Path> rolledPath;

        if (shouldRoll(bytes.length)) {
            rolledPath = roll();
        } else {
            rolledPath = Optional.empty();
        }

        if (outputStream == null) {
            open();
        }

        outputStream.write(bytes);
        outputStream.flush();

        eventCount++;
        byteCount += bytes.length;

        return rolledPath;
    }

    public synchronized void open() throws IOException {
        if (outputStream == null) {
            final Path file = dir.resolve(EventStoreFile.createTempFileName(prefix));
            if (Files.isRegularFile(file)) {
                outputStream = new BufferedOutputStream(Files.newOutputStream(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND));
            } else {
                outputStream = new BufferedOutputStream(Files.newOutputStream(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW));
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
    }

    private boolean shouldRoll(final long addBytes) {
        return lastRoll.isBefore(Instant.now().minus(eventStoreConfig.getMaxAge())) ||
                eventCount >= eventStoreConfig.getMaxEventCount() ||
                byteCount + addBytes > eventStoreConfig.getMaxByteCount();
    }

    public synchronized Optional<Path> tryRoll() throws IOException {
        if (shouldRoll(0)) {
            return roll();
        }
        return Optional.empty();
    }

    public synchronized Optional<Path> roll() throws IOException {
        if (eventCount > 0) {
            close();
            final Path file = dir.resolve(EventStoreFile.createTempFileName(prefix));
            if (Files.isRegularFile(file)) {
                final Path rolledFile = dir.resolve(EventStoreFile.createRolledFileName(prefix));
                Files.move(file, rolledFile, StandardCopyOption.ATOMIC_MOVE);
                eventCount = 0;
                byteCount = 0;
                lastRoll = Instant.now();

                return Optional.of(rolledFile);
            }
        }

        return Optional.empty();
    }
}
