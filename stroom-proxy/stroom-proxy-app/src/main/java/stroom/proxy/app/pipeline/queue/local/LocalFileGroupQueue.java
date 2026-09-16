/*
 * Copyright 2026 Crown Copyright
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

import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * A {@link FileGroupQueue} on this node's disk, owned by this one process. Local mode only.
 * <pre>
 * {@code <root>/
 * ├── .queue-owner.lock          held for the process's life; a second process is refused
 * ├── pending/<id>.json          deliverable, FIFO by id
 * ├── in-flight/<id>.json        claimed by a consumer thread of this process
 * ├── failed/<id>.<reason>.<millis>.json   (+ .error.txt)   given up on
 * └── tmp/                       a publish in progress}
 * </pre>
 * <p>
 * The single owner is what keeps this simple. A message is claimed by renaming its file into
 * {@code in-flight/}, and that rename is the whole claim: every consumer is a thread of the process
 * holding the lock, so an item is claimed exactly while a consumer holds it, and a consumer that
 * stops holding one without completing it releases it through {@link FileGroupQueueItem#close()}.
 * A process that dies leaves its in-flight files to start-up recovery. Nothing has to guess whether
 * a consumer is alive.
 * </p>
 */
public class LocalFileGroupQueue implements FileGroupQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalFileGroupQueue.class);

    private static final String PENDING_DIR_NAME = "pending";
    private static final String IN_FLIGHT_DIR_NAME = "in-flight";
    private static final String FAILED_DIR_NAME = "failed";
    private static final String TEMP_DIR_NAME = "tmp";
    private static final String OWNERSHIP_LOCK_FILE_NAME = ".queue-owner.lock";
    /** The {@code failed/} reason for a message that could not be decoded. */
    static final String INVALID_MESSAGE_REASON = "invalid-message";
    private static final String MESSAGE_FILE_EXTENSION = ".json";
    private static final int SEQUENCE_WIDTH = 20;
    private static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 100;

    /**
     * The delivery count travels in the message, rewritten on each requeue, which is what lets a
     * requeue take a new id and go to the tail.
     */
    static final String DELIVERY_ATTEMPTS_ATTRIBUTE = "queue.deliveryAttempts";

    private final String name;
    private final Path root;
    private final Path pendingDir;
    private final Path inFlightDir;
    private final Path failedDir;
    private final Path tempDir;
    private final FileGroupQueueMessageCodec codec;
    private final int maxDeliveryAttempts;
    private final Durability durability;
    private final FileLock ownershipLock;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong publishCounter = new AtomicLong();
    private final ReentrantLock workLock = new ReentrantLock();
    private final Condition workAvailable = workLock.newCondition();

    public LocalFileGroupQueue(final String name, final Path root) throws IOException {
        this(name, root, new FileGroupQueueMessageCodec(), DEFAULT_MAX_DELIVERY_ATTEMPTS, Durability.FULL);
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec) throws IOException {
        this(name, root, codec, DEFAULT_MAX_DELIVERY_ATTEMPTS, Durability.FULL);
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec,
                               final int maxDeliveryAttempts) throws IOException {
        this(name, root, codec, maxDeliveryAttempts, Durability.FULL);
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec,
                               final int maxDeliveryAttempts,
                               final Durability durability) throws IOException {
        this.name = requireNonBlank(name, "name");
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException("maxDeliveryAttempts must be >= 1, got " + maxDeliveryAttempts);
        }
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.durability = Objects.requireNonNull(durability, "durability");
        this.pendingDir = this.root.resolve(PENDING_DIR_NAME);
        this.inFlightDir = this.root.resolve(IN_FLIGHT_DIR_NAME);
        this.failedDir = this.root.resolve(FAILED_DIR_NAME);
        this.tempDir = this.root.resolve(TEMP_DIR_NAME);

        Files.createDirectories(this.root);
        this.ownershipLock = takeExclusiveOwnership();
        try {
            Files.createDirectories(pendingDir);
            Files.createDirectories(inFlightDir);
            Files.createDirectories(failedDir);
            FileUtil.deleteContents(tempDir);
            Files.createDirectories(tempDir);
            // Seed before recovering: recovery requeues through writePending, which allocates ids.
            sequence.set(Math.max(highestIdIn(pendingDir), Math.max(highestIdIn(inFlightDir), highestIdIn(failedDir))));
            recoverInFlightMessages();
        } catch (final IOException | RuntimeException e) {
            releaseOwnership();
            throw e;
        }
    }

    /**
     * A local queue belongs to exactly one queue in one process. Two sharing a directory would each
     * recover the other's in-flight work at start-up and hand the same message to two consumers.
     */
    private FileLock takeExclusiveOwnership() throws IOException {
        final Path lockFile = root.resolve(OWNERSHIP_LOCK_FILE_NAME);
        final FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (final OverlappingFileLockException e) {
            // Same JVM: two queue definitions given the same path. The same mistake to an operator.
            lock = null;
        } catch (final IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
        if (lock == null) {
            channel.close();
            throw new IOException(LogUtil.message(
                    "Queue '{}' cannot use {} because it is already in use. A LOCAL_FILESYSTEM queue is owned "
                    + "by exactly one queue in one process. Give each its own directory, or use an SQS or Kafka "
                    + "queue if the point was to share one.",
                    name, FileUtil.getCanonicalPath(root)));
        }
        return lock;
    }

    private void releaseOwnership() {
        try {
            ownershipLock.release();
            ownershipLock.channel().close();
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message(
                    "Queue {} could not release its ownership lock; the OS releases it when this process exits",
                    name), e);
        }
    }

    /**
     * For tests: drop the ownership lock without closing, as a dead process would.
     */
    public void simulateProcessDeath() throws IOException {
        ownershipLock.release();
        ownershipLock.channel().close();
    }

    /**
     * A crash mid-processing was an attempt: every in-flight message goes back to pending with its
     * count incremented, or to failed if that exhausts it.
     */
    private void recoverInFlightMessages() throws IOException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inFlightDir, "*" + MESSAGE_FILE_EXTENSION)) {
            for (final Path inFlightFile : stream) {
                final byte[] bytes;
                try {
                    bytes = Files.readAllBytes(inFlightFile);
                } catch (final IOException e) {
                    // A failed read says nothing about the message: back to pending as it is.
                    LOGGER.warn(() -> LogUtil.message(
                            "Queue {} could not read in-flight message {} during recovery; returning it to pending "
                            + "unread", name, inFlightFile), e);
                    Files.move(inFlightFile, pendingDir.resolve(inFlightFile.getFileName()),
                            StandardCopyOption.ATOMIC_MOVE);
                    continue;
                }
                final FileGroupQueueMessage message;
                try {
                    message = codec.fromBytes(bytes);
                } catch (final IOException | RuntimeException e) {
                    moveToFailed(inFlightFile, INVALID_MESSAGE_REASON, e);
                    continue;
                }
                requeue(inFlightFile, message, null);
            }
        }
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
        writePending(message);
    }

    /**
     * Write to {@code tmp/}, force the bytes, rename into {@code pending/}, force the directory. The
     * rename refuses to overwrite: an id collision is a loud failure, never a lost message.
     */
    private void writePending(final FileGroupQueueMessage message) throws IOException {
        final String itemId = formatSequence(sequence.incrementAndGet());
        final Path destination = pendingDir.resolve(itemId + MESSAGE_FILE_EXTENSION);
        final Path tempFile = Files.createTempFile(tempDir, itemId + "-", MESSAGE_FILE_EXTENSION + ".tmp");
        try {
            writeDurably(tempFile, codec.toBytes(message));
            if (Files.exists(destination)) {
                throw new FileAlreadyExistsException(destination.toString(), null,
                        "Queue sequence collision on '" + name + "' - refusing to overwrite a queued message");
            }
            Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE);
            // The bytes are one half; the directory entry that publishes them is the other.
            if (durability.forcesQueueMessages()) {
                DirUtil.fsyncDir(pendingDir);
            }
            signalWork();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void writeDurably(final Path file, final byte[] content) throws IOException {
        try (final FileChannel channel = FileChannel.open(
                file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(content));
            if (durability.forcesQueueMessages()) {
                channel.force(true);
            }
        }
    }

    @Override
    public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
        final long deadlineNanos = System.nanoTime() + Math.max(0L, maxWait.toNanos());
        while (true) {
            // Read the publish counter before looking, so a publish that lands between the look and
            // the wait cannot be missed.
            final long seen = publishCounter.get();
            final Optional<FileGroupQueueItem> item = tryTakeNext();
            if (item.isPresent()) {
                return item;
            }
            if (!awaitWork(seen, deadlineNanos)) {
                return Optional.empty();
            }
        }
    }

    /**
     * Take the lowest id in {@code pending/} by renaming it into {@code in-flight/}. A thread that
     * loses the rename to another thread tries the next.
     */
    private Optional<FileGroupQueueItem> tryTakeNext() throws IOException {
        while (true) {
            final Optional<Path> optionalPendingFile = findNextPendingFile();
            if (optionalPendingFile.isEmpty()) {
                return Optional.empty();
            }
            final Path pendingFile = optionalPendingFile.get();
            final String itemId = itemIdFromFile(pendingFile);
            final Path inFlightFile = inFlightDir.resolve(pendingFile.getFileName());

            try {
                // ATOMIC_MOVE silently replaces its target, so the check has to be explicit. Ids are
                // never reused while the process lives, so this is a corrupted directory, not a race.
                if (Files.exists(inFlightFile)) {
                    throw new FileAlreadyExistsException(inFlightFile.toString(), null,
                            "In-flight message already exists for id '" + itemId + "' on queue '" + name + "'");
                }
                Files.move(pendingFile, inFlightFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (final NoSuchFileException e) {
                if (Files.exists(pendingFile)) {
                    throw e;
                }
                continue; // Another thread won the rename.
            } catch (final FileAlreadyExistsException e) {
                moveToFailed(pendingFile, "duplicate-pending", e);
                continue;
            }

            final byte[] bytes;
            try {
                bytes = Files.readAllBytes(inFlightFile);
            } catch (final IOException e) {
                // A failed read says nothing about the message, so give it back rather than condemn it.
                try {
                    Files.move(inFlightFile, pendingFile, StandardCopyOption.ATOMIC_MOVE);
                } catch (final IOException giveBackFailure) {
                    e.addSuppressed(giveBackFailure);
                }
                throw e;
            }
            final FileGroupQueueMessage message;
            try {
                message = codec.fromBytes(bytes);
            } catch (final IOException | RuntimeException e) {
                // The bytes were read and cannot be decoded, so this message really is unusable.
                moveToFailed(inFlightFile, INVALID_MESSAGE_REASON, e);
                throw new IOException("Unable to decode queue message " + inFlightFile, e);
            }
            return Optional.of(new Item(itemId, message, inFlightFile));
        }
    }

    private boolean awaitWork(final long seen, final long deadlineNanos) {
        workLock.lock();
        try {
            while (publishCounter.get() == seen) {
                final long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                workAvailable.awaitNanos(remaining);
            }
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            workLock.unlock();
        }
    }

    private void signalWork() {
        publishCounter.incrementAndGet();
        workLock.lock();
        try {
            workAvailable.signalAll();
        } finally {
            workLock.unlock();
        }
    }

    /**
     * Put an in-flight message back at the tail with its attempt counted, or into {@code failed/}
     * once the attempts are exhausted. The replacement is written before the original is removed,
     * so a crash between the two costs a duplicate rather than a loss.
     */
    private void requeue(final Path inFlightFile,
                         final FileGroupQueueMessage message,
                         final Throwable error) throws IOException {
        final int attempts = deliveryAttempts(message) + 1;
        if (attempts >= maxDeliveryAttempts) {
            LOGGER.error(() -> LogUtil.message(
                    "Queue {} giving up on message {} after {} delivery attempts; it is in {}",
                    name, message.messageId(), attempts, failedDir));
            moveToFailed(inFlightFile, "max-delivery-attempts", error);
            return;
        }
        writePending(message.withAttribute(DELIVERY_ATTEMPTS_ATTRIBUTE, Integer.toString(attempts)));
        Files.deleteIfExists(inFlightFile);
    }

    static int deliveryAttempts(final FileGroupQueueMessage message) {
        final String raw = message.attributes().get(DELIVERY_ATTEMPTS_ATTRIBUTE);
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private void moveToFailed(final Path file, final String reason, final Throwable error) throws IOException {
        if (file == null || !Files.exists(file)) {
            return;
        }
        final Path failedFile = failedDir.resolve(
                itemIdFromFile(file) + "." + reason + "." + System.currentTimeMillis() + MESSAGE_FILE_EXTENSION);
        Files.move(file, failedFile, StandardCopyOption.ATOMIC_MOVE);
        if (error != null) {
            Files.writeString(failedFile.resolveSibling(failedFile.getFileName() + ".error.txt"),
                    stackTrace(error), StandardCharsets.UTF_8);
        }
    }

    /**
     * Every file store location a message in this queue names - pending, in flight and failed
     * alike, since a quarantined message is replayable and its group must stay. For the local-mode
     * start-up sweep, which runs while nothing else does.
     *
     * <p>
     * A file this queue itself quarantined as undecodable is skipped rather than read: it was
     * undecodable when it was received and is still undecodable, so it names no group this proxy
     * could ever resolve, and reading it would only make the sweep refuse to run on every start
     * for as long as the file sat there.
     * </p>
     *
     * @throws IOException If any other message file cannot be read or parsed. A group whose message
     *                     is unreadable would otherwise be swept as an orphan, so the caller must not
     *                     sweep at all.
     */
    public Set<FileStoreLocation> readAllLocations() throws IOException {
        final Set<FileStoreLocation> locations = new HashSet<>();
        for (final Path dir : new Path[]{pendingDir, inFlightDir, failedDir}) {
            try (final Stream<Path> files = Files.list(dir)) {
                for (final Path file : files.filter(LocalFileGroupQueue::isMessageFile).toList()) {
                    if (dir.equals(failedDir) && isQuarantinedAsInvalid(file)) {
                        continue;
                    }
                    try {
                        locations.add(codec.fromBytes(Files.readAllBytes(file)).fileStoreLocation());
                    } catch (final IOException | RuntimeException e) {
                        throw new IOException("Cannot read queue message " + file + ": " + e.getMessage(), e);
                    }
                }
            }
        }
        return locations;
    }

    @Override
    public void close() {
        releaseOwnership();
    }

    @Override
    public HealthCheck.Result healthCheck() {
        try {
            final boolean pendingOk = Files.isDirectory(pendingDir) && Files.isWritable(pendingDir);
            final boolean inFlightOk = Files.isDirectory(inFlightDir) && Files.isWritable(inFlightDir);
            if (!pendingOk || !inFlightOk) {
                return HealthCheck.Result.builder()
                        .unhealthy()
                        .withMessage("Directory check failed: pending=%s, inFlight=%s", pendingOk, inFlightOk)
                        .build();
            }
            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("pendingCount", getApproximatePendingCount())
                    .withDetail("inFlightCount", getApproximateInFlightCount())
                    .withDetail("failedCount", getApproximateFailedCount())
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

    private static long highestIdIn(final Path dir) throws IOException {
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .mapToLong(LocalFileGroupQueue::idFromFileOrZero)
                    .max()
                    .orElse(0L);
        }
    }

    private static long idFromFileOrZero(final Path file) {
        final String id = itemIdFromFile(file);
        final int dot = id.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? id : id.substring(0, dot));
        } catch (final NumberFormatException e) {
            return 0L;
        }
    }

    private Optional<Path> findNextPendingFile() throws IOException {
        try (final Stream<Path> stream = Files.list(pendingDir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .min(Comparator.comparing(path -> path.getFileName().toString()));
        }
    }

    private static long countMessageFiles(final Path dir) throws IOException {
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream.filter(LocalFileGroupQueue::isMessageFile).count();
        }
    }

    /** A {@code failed/} file named {@code <id>.invalid-message.<millis>.json} by {@link #moveToFailed}. */
    private static boolean isQuarantinedAsInvalid(final Path file) {
        return file.getFileName().toString().contains("." + INVALID_MESSAGE_REASON + ".");
    }

    private static boolean isMessageFile(final Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(MESSAGE_FILE_EXTENSION);
    }

    private static String itemIdFromFile(final Path file) {
        final String fileName = file.getFileName().toString();
        return fileName.endsWith(MESSAGE_FILE_EXTENSION)
                ? fileName.substring(0, fileName.length() - MESSAGE_FILE_EXTENSION.length())
                : fileName;
    }

    private static String formatSequence(final long sequence) {
        final String value = Long.toString(sequence);
        return value.length() >= SEQUENCE_WIDTH
                ? value
                : "0".repeat(SEQUENCE_WIDTH - value.length()) + value;
    }

    private static String stackTrace(final Throwable error) {
        final StringWriter stringWriter = new StringWriter();
        try (final PrintWriter printWriter = new PrintWriter(stringWriter)) {
            error.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    private final class Item implements FileGroupQueueItem {

        private final String itemId;
        private final FileGroupQueueMessage message;
        private final Path inFlightFile;
        private boolean completed;

        private Item(final String itemId, final FileGroupQueueMessage message, final Path inFlightFile) {
            this.itemId = itemId;
            this.message = message;
            this.inFlightFile = inFlightFile;
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
        public int getDeliveryAttempt() {
            return deliveryAttempts(message) + 1;
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
            if (Files.exists(inFlightFile)) {
                requeue(inFlightFile, message, error);
            }
            completed = true;
        }

        @Override
        public void close() throws IOException {
            if (!completed) {
                // Neither acknowledged nor failed: the holder has stopped holding it, so it goes back.
                // If this throws too the file stays in in-flight/ and start-up recovery takes it.
                fail(null);
            }
        }
    }
}
