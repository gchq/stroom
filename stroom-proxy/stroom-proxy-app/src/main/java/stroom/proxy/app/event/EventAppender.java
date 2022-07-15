package stroom.proxy.app.event;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;

class EventAppender implements AutoCloseable {
    private final Path dir;
    private final String prefix;
    private BufferedWriter writer;
    private Instant lastRoll;

    public EventAppender(final Path dir,
                         final FeedKey feedKey) {
        this.dir = dir;
        prefix = feedKey.encodeKey();
        lastRoll = Instant.now();
    }

    public synchronized void write(final String data) throws IOException {
        if (writer == null) {
            open();
        }

        writer.write(data);
        writer.flush();
    }

    public synchronized void open() throws IOException {
        if (writer == null) {
            final Path file = dir.resolve(EventStoreFile.createTempFileName(prefix));
            if (Files.isRegularFile(file)) {
                writer = Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } else {
                writer = Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW);
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    public synchronized Optional<Path> roll() throws IOException {
        close();
        final Path file = dir.resolve(EventStoreFile.createTempFileName(prefix));
        if (Files.isRegularFile(file)) {
            final Path rolledFile = dir.resolve(EventStoreFile.createRolledFileName(prefix));
            Files.move(file, rolledFile, StandardCopyOption.ATOMIC_MOVE);
            lastRoll = Instant.now();

            return Optional.of(rolledFile);
        }

        return Optional.empty();
    }

    public Instant getLastRoll() {
        return lastRoll;
    }
}
