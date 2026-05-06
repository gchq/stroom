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

package stroom.proxy.app.pipeline.queue.local;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Local/simple filesystem implementation of {@link FileGroupQueue}.
 * <p>
 * This queue stores reference messages as persisted JSON files. It does not move
 * or mutate the data referenced by {@link FileGroupQueueMessage#fileStoreLocation()}.
 * </p>
 * <p>
 * Queue layout:
 * </p>
 * <ul>
 *     <li>{@code sequence.txt} - global sequence file for this named local queue</li>
 *     <li>{@code pending/} - messages available to consumers</li>
 *     <li>{@code in-flight/} - messages leased to consumers</li>
 *     <li>{@code failed/} - corrupt messages or duplicates that cannot be safely retried</li>
 *     <li>{@code .tmp/} - temporary files used during publication</li>
 * </ul>
 * <p>
 * The queue provides at-least-once delivery. If a process stops with messages in
 * {@code in-flight/}, constructing the queue again recovers those messages by
 * moving them back to {@code pending/}.
 * </p>
 */
public class LocalFileGroupQueue implements FileGroupQueue {

    private static final String PENDING_DIR_NAME = "pending";
    private static final String IN_FLIGHT_DIR_NAME = "in-flight";
    private static final String FAILED_DIR_NAME = "failed";
    private static final String TEMP_DIR_NAME = "tmp";
    private static final String SEQUENCE_FILE_NAME = "sequence.txt";
    private static final String MESSAGE_FILE_EXTENSION = ".json";
    private static final int SEQUENCE_WIDTH = 20;

    private final String name;
    private final Path root;
    private final Path pendingDir;
    private final Path inFlightDir;
    private final Path failedDir;
    private final Path tempDir;
    private final Path sequenceFile;
    private final FileGroupQueueMessageCodec codec;

    public LocalFileGroupQueue(final String name,
                               final Path root) throws IOException {
        this(name, root, new FileGroupQueueMessageCodec());
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec) throws IOException {
        this.name = requireNonBlank(name, "name");
        this.root = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize();
        this.codec = Objects.requireNonNull(codec, "codec");

        this.pendingDir = this.root.resolve(PENDING_DIR_NAME);
        this.inFlightDir = this.root.resolve(IN_FLIGHT_DIR_NAME);
        this.failedDir = this.root.resolve(FAILED_DIR_NAME);
        this.tempDir = this.root.resolve(TEMP_DIR_NAME);
        this.sequenceFile = this.root.resolve(SEQUENCE_FILE_NAME);

        initialise();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public QueueType getType() {
        return QueueType.LOCAL_FILESYSTEM;
    }

    public Path getRoot() {
        return root;
    }

    public Path getPendingDir() {
        return pendingDir;
    }

    public Path getInFlightDir() {
        return inFlightDir;
    }

    public Path getFailedDir() {
        return failedDir;
    }

    @Override
    public void publish(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");

        if (!name.equals(message.queueName())) {
            throw new IllegalArgumentException("Message queueName '" + message.queueName()
                                               + "' does not match queue '" + name + "'");
        }

        final long sequence = allocateSequence();
        final String itemId = formatSequence(sequence);
        final Path destination = pendingDir.resolve(itemId + MESSAGE_FILE_EXTENSION);
        final Path tempFile = Files.createTempFile(tempDir, itemId + "-", MESSAGE_FILE_EXTENSION + ".tmp");

        try {
            Files.write(tempFile, codec.toBytes(message));
            moveAtomically(tempFile, destination);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public Optional<FileGroupQueueItem> next() throws IOException {
        while (true) {
            final Optional<Path> optionalPendingFile = findNextPendingFile();
            if (optionalPendingFile.isEmpty()) {
                return Optional.empty();
            }

            final Path pendingFile = optionalPendingFile.get();
            final String itemId = itemIdFromFile(pendingFile);
            final Path inFlightFile = inFlightDir.resolve(pendingFile.getFileName());

            try {
                moveAtomically(pendingFile, inFlightFile);
            } catch (final NoSuchFileException e) {
                // Another local consumer in this JVM/process may have won the race.
                continue;
            } catch (final FileAlreadyExistsException e) {
                moveToFailed(pendingFile, "duplicate-pending", e);
                continue;
            }

            final FileGroupQueueMessage message;
            try {
                message = codec.fromBytes(Files.readAllBytes(inFlightFile));
            } catch (final Exception e) {
                moveToFailed(inFlightFile, "invalid-message", e);
                throw new IOException("Unable to read queue message " + inFlightFile, e);
            }

            return Optional.of(new LocalFileGroupQueueItem(itemId, message, inFlightFile));
        }
    }

    @Override
    public void close() {
        // No open resources are held between operations.
    }

    @Override
    public HealthCheck.Result healthCheck() {
        try {
            final boolean pendingOk = Files.isDirectory(pendingDir)
                                      && Files.isWritable(pendingDir);
            final boolean inFlightOk = Files.isDirectory(inFlightDir)
                                       && Files.isWritable(inFlightDir);

            if (!pendingOk || !inFlightOk) {
                return HealthCheck.Result.builder()
                        .unhealthy()
                        .withMessage("Directory check failed: pending=%s, inFlight=%s",
                                pendingOk, inFlightOk)
                        .build();
            }

            final long pending = getApproximatePendingCount();
            final long inflight = getApproximateInFlightCount();
            final long failed = getApproximateFailedCount();

            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("pendingCount", pending)
                    .withDetail("inFlightCount", inflight)
                    .withDetail("failedCount", failed)
                    .build();

        } catch (final Exception e) {
            return HealthCheck.Result.unhealthy(e);
        }
    }

    public long getApproximatePendingCount() throws IOException {
        return countMessageFiles(pendingDir);
    }

    public long getApproximateInFlightCount() throws IOException {
        return countMessageFiles(inFlightDir);
    }

    public long getApproximateFailedCount() throws IOException {
        return countMessageFiles(failedDir);
    }

    public Optional<Instant> getOldestPendingItemTime() throws IOException {
        try (final Stream<Path> stream = Files.list(pendingDir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .map(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (final IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder());
        }
    }

    private void initialise() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(pendingDir);
        Files.createDirectories(inFlightDir);
        Files.createDirectories(failedDir);
        Files.createDirectories(tempDir);

        if (!Files.exists(sequenceFile)) {
            Files.writeString(
                    sequenceFile,
                    "0\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }

        recoverInFlightMessages();
    }

    private void recoverInFlightMessages() throws IOException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inFlightDir, "*" + MESSAGE_FILE_EXTENSION)) {
            for (final Path inFlightFile : stream) {
                final Path pendingFile = pendingDir.resolve(inFlightFile.getFileName());
                if (Files.exists(pendingFile)) {
                    moveToFailed(inFlightFile, "recovered-duplicate", null);
                } else {
                    moveAtomically(inFlightFile, pendingFile);
                }
            }
        }
    }

    private long allocateSequence() throws IOException {
        try (final FileChannel channel = FileChannel.open(
                sequenceFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
             final FileLock ignored = channel.lock()) {
            final long current = readSequence(channel);
            final long next = current + 1;

            channel.truncate(0);
            channel.position(0);
            channel.write(ByteBuffer.wrap((Long.toString(next) + "\n").getBytes(StandardCharsets.UTF_8)));
            channel.force(true);

            return next;
        }
    }

    private long readSequence(final FileChannel channel) throws IOException {
        channel.position(0);

        final ByteBuffer buffer = ByteBuffer.allocate(64);
        final int bytesRead = channel.read(buffer);
        if (bytesRead <= 0) {
            return 0;
        }

        buffer.flip();
        final String value = StandardCharsets.UTF_8.decode(buffer).toString().trim();
        if (value.isEmpty()) {
            return 0;
        }

        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid queue sequence value in " + sequenceFile + ": " + value, e);
        }
    }

    private Optional<Path> findNextPendingFile() throws IOException {
        try (final Stream<Path> stream = Files.list(pendingDir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .min(Comparator.comparing(path -> path.getFileName().toString()));
        }
    }

    private long countMessageFiles(final Path dir) throws IOException {
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .count();
        }
    }

    private static boolean isMessageFile(final Path path) {
        return Files.isRegularFile(path)
               && path.getFileName().toString().endsWith(MESSAGE_FILE_EXTENSION);
    }

    private static String itemIdFromFile(final Path file) {
        final String fileName = file.getFileName().toString();
        if (fileName.endsWith(MESSAGE_FILE_EXTENSION)) {
            return fileName.substring(0, fileName.length() - MESSAGE_FILE_EXTENSION.length());
        }
        return fileName;
    }

    private static String formatSequence(final long sequence) {
        final String value = Long.toString(sequence);
        if (value.length() >= SEQUENCE_WIDTH) {
            return value;
        }
        return "0".repeat(SEQUENCE_WIDTH - value.length()) + value;
    }

    private static void moveAtomically(final Path source,
                                       final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private void moveToFailed(final Path file,
                              final String reason,
                              final Throwable error) throws IOException {
        if (file == null || !Files.exists(file)) {
            return;
        }

        final String failedFileName = itemIdFromFile(file)
                                      + "."
                                      + reason
                                      + "."
                                      + System.currentTimeMillis()
                                      + MESSAGE_FILE_EXTENSION;
        final Path failedFile = failedDir.resolve(failedFileName);
        moveAtomically(file, failedFile);

        if (error != null) {
            final Path errorFile = failedFile.resolveSibling(failedFile.getFileName() + ".error.txt");
            Files.writeString(errorFile, stackTrace(error), StandardCharsets.UTF_8);
        }
    }

    private static String stackTrace(final Throwable error) {
        final StringWriter stringWriter = new StringWriter();
        try (final PrintWriter printWriter = new PrintWriter(stringWriter)) {
            error.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private final class LocalFileGroupQueueItem implements FileGroupQueueItem {

        private final String itemId;
        private final FileGroupQueueMessage message;
        private final Path inFlightFile;
        private boolean completed;

        private LocalFileGroupQueueItem(final String itemId,
                                        final FileGroupQueueMessage message,
                                        final Path inFlightFile) {
            this.itemId = Objects.requireNonNull(itemId, "itemId");
            this.message = Objects.requireNonNull(message, "message");
            this.inFlightFile = Objects.requireNonNull(inFlightFile, "inFlightFile");
        }

        @Override
        public String getId() {
            return itemId;
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return message;
        }

        @Override
        public Map<String, String> getMetadata() {
            final Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("queueName", name);
            metadata.put("queueType", QueueType.LOCAL_FILESYSTEM.name());
            metadata.put("itemId", itemId);
            metadata.put("state", completed ? "completed" : "in-flight");
            metadata.put("inFlightPath", inFlightFile.toString());
            metadata.put("root", root.toString());
            return Map.copyOf(metadata);
        }

        @Override
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }

            Files.deleteIfExists(inFlightFile);
            completed = true;
        }

        @Override
        public void fail(final Throwable error) throws IOException {
            if (completed) {
                return;
            }

            if (!Files.exists(inFlightFile)) {
                completed = true;
                return;
            }

            final Path pendingFile = pendingDir.resolve(inFlightFile.getFileName());
            if (Files.exists(pendingFile)) {
                moveToFailed(inFlightFile, "fail-duplicate-pending", error);
            } else {
                final Path errorFile = inFlightFile.resolveSibling(inFlightFile.getFileName() + ".last-error.txt");
                if (error != null) {
                    Files.writeString(errorFile, stackTrace(error), StandardCharsets.UTF_8);
                }
                moveAtomically(inFlightFile, pendingFile);
                Files.deleteIfExists(errorFile);
            }

            completed = true;
        }

        @Override
        public void close() {
            // Deliberately no-op. Callers must acknowledge() or fail(Throwable).
            // Unacknowledged in-flight messages are recovered on queue restart.
        }
    }
}
