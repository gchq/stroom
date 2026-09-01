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

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
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

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalFileGroupQueue.class);

    private static final String PENDING_DIR_NAME = "pending";
    private static final String IN_FLIGHT_DIR_NAME = "in-flight";
    private static final String FAILED_DIR_NAME = "failed";
    private static final String TEMP_DIR_NAME = "tmp";
    private static final String SEQUENCE_FILE_NAME = "sequence.txt";
    private static final String MESSAGE_FILE_EXTENSION = ".json";
    private static final int SEQUENCE_WIDTH = 20;

    /**
     * How often an idle queue looks for leases abandoned by a consumer that never
     * acknowledged or failed its item. Only runs when there is nothing pending,
     * so it costs a directory scan on an otherwise idle queue.
     */
    private static final Duration DEFAULT_ABANDONED_LEASE_SCAN_INTERVAL = Duration.ofSeconds(10);

    /**
     * Message attribute carrying how many times this message has been delivered.
     * <p>
     * It travels in the message rather than in the file name so that re-queuing can
     * allocate a fresh id, which is what stops a failing message from holding the
     * head of the queue.
     * </p>
     */
    static final String DELIVERY_ATTEMPTS_ATTRIBUTE = "queue.deliveryAttempts";

    private static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 100;

    private final String name;
    private final Path root;
    private final Path pendingDir;
    private final Path inFlightDir;
    private final Path failedDir;
    private final Path tempDir;
    private final Path sequenceFile;
    private final FileGroupQueueMessageCodec codec;

    /**
     * Item id allocator.
     * <p>
     * Seeded at construction from the greater of the persisted counter and the
     * highest id actually present on disk, so a lost, truncated or restored
     * {@code sequence.txt} can never cause a previously queued message to be
     * overwritten. Allocation is a plain atomic increment - no file locking.
     * </p>
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Ids of items currently leased to a live consumer in this process.
     * <p>
     * An id is added <em>before</em> the pending file is moved into
     * {@code in-flight} and removed when the item is closed, so an in-flight file
     * whose id is absent from this set is definitionally abandoned - no live
     * consumer can still be holding it. That is what makes
     * {@link #reclaimAbandonedLeases()} safe without a visibility timeout: the
     * local queue is confined to one process, so it can know this exactly rather
     * than having to guess from elapsed time the way SQS does.
     * </p>
     */
    private final Set<String> activeLeases = ConcurrentHashMap.newKeySet();

    private final Duration abandonedLeaseScanInterval;
    private final int maxDeliveryAttempts;
    private final AtomicLong lastAbandonedLeaseScanMs = new AtomicLong();
    private final ReentrantLock workLock = new ReentrantLock();
    private final Condition workAvailable = workLock.newCondition();
    /** Bumped whenever something lands in pending/, so a waiter cannot miss a publish. */
    private final AtomicLong publishCounter = new AtomicLong();
    private final AtomicBoolean scanInProgress = new AtomicBoolean();

    public LocalFileGroupQueue(final String name,
                               final Path root) throws IOException {
        this(name, root, new FileGroupQueueMessageCodec());
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec) throws IOException {
        this(name, root, codec, DEFAULT_ABANDONED_LEASE_SCAN_INTERVAL, DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec,
                               final Duration abandonedLeaseScanInterval) throws IOException {
        this(name, root, codec, abandonedLeaseScanInterval, DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    /**
     * @param abandonedLeaseScanInterval Minimum gap between scans for abandoned
     * leases. {@link Duration#ZERO} scans on every empty poll, which is what the
     * tests want and no deployment does.
     */
    public LocalFileGroupQueue(final String name,
                               final Path root,
                               final FileGroupQueueMessageCodec codec,
                               final Duration abandonedLeaseScanInterval,
                               final int maxDeliveryAttempts) throws IOException {
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException("maxDeliveryAttempts must be >= 1, got " + maxDeliveryAttempts);
        }
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.abandonedLeaseScanInterval =
                Objects.requireNonNull(abandonedLeaseScanInterval, "abandonedLeaseScanInterval");
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

        writePending(message);
    }

    /**
     * Write a message into {@code pending/} under a freshly allocated id.
     */
    private void writePending(final FileGroupQueueMessage message) throws IOException {
        final String itemId = formatSequence(allocateSequence());
        final Path destination = pendingDir.resolve(itemId + MESSAGE_FILE_EXTENSION);
        final Path tempFile = Files.createTempFile(tempDir, itemId + "-", MESSAGE_FILE_EXTENSION + ".tmp");

        try {
            writeDurably(tempFile, codec.toBytes(message));

            // Never overwrite an already queued message. With the sequence seeded from
            // disk this should be unreachable, but ATOMIC_MOVE silently clobbers its
            // target, so a collision must fail loudly rather than destroy data.
            if (Files.exists(destination)) {
                throw new FileAlreadyExistsException(
                        destination.toString(),
                        null,
                        "Queue sequence collision on '" + name + "' - refusing to overwrite "
                        + "an existing queued message");
            }

            moveAtomically(tempFile, destination);
            signalWork();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * @return How many times the given message has already been delivered.
     */
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

    private static FileGroupQueueMessage withDeliveryAttempts(final FileGroupQueueMessage message,
                                                              final int attempts) {
        final Map<String, String> attributes = new LinkedHashMap<>(message.attributes());
        attributes.put(DELIVERY_ATTEMPTS_ATTRIBUTE, Integer.toString(attempts));

        // Keep the messageId and createdTime, so a re-queued message stays traceable
        // as the same message rather than looking like a new arrival.
        return FileGroupQueueMessage.create(
                message.messageId(),
                message.queueName(),
                message.fileGroupId(),
                message.fileStoreLocation(),
                message.producingStage(),
                message.producerId(),
                message.createdTime(),
                message.traceId(),
                attributes);
    }

    @Override
    public Optional<FileGroupQueueItem> next() throws IOException {
        return next(Duration.ZERO);
    }

    @Override
    public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
        final long deadlineNanos = System.nanoTime() + Math.max(0L, maxWait.toNanos());
        while (true) {
            // Read the publish counter BEFORE looking, so a publish that lands between the look and
            // the wait cannot be missed - awaitWork returns immediately if the counter has moved.
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
     * Wait for a publish, or for the deadline.
     *
     * @return true if there may now be work, false if the deadline passed or the thread was
     * interrupted.
     */
    private boolean awaitWork(final long seen, final long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return false;
        }
        workLock.lock();
        try {
            while (publishCounter.get() == seen) {
                remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                if (workAvailable.awaitNanos(remaining) <= 0 && publishCounter.get() == seen) {
                    return false;
                }
            }
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            workLock.unlock();
        }
    }

    /**
     * Wake any consumer waiting in {@link #next(Duration)}. Called whenever something lands in
     * {@code pending/} - a publish, a failed item being re-queued, or a reclaimed lease.
     */
    private void signalWork() {
        publishCounter.incrementAndGet();
        workLock.lock();
        try {
            workAvailable.signalAll();
        } finally {
            workLock.unlock();
        }
    }

    private Optional<FileGroupQueueItem> tryTakeNext() throws IOException {
        while (true) {
            // Reclaim before looking for work, not only when the queue looks empty. Driving the scan
            // off an empty poll meant a queue that always had something pending never ran it at all,
            // so an in-flight item whose lease was released without an acknowledge or a fail stayed in
            // in-flight/ for the life of the process - the exact stall the scan exists to end. The
            // interval CAS inside this call already bounds how often a scan actually runs, so calling
            // it on every poll costs a timestamp comparison. Anything reclaimed lands in pending/ and
            // is picked up by the findNextPendingFile below in this same iteration.
            maybeReclaimAbandonedLeases();

            final Optional<Path> optionalPendingFile = findNextPendingFile();
            if (optionalPendingFile.isEmpty()) {
                return Optional.empty();
            }

            final Path pendingFile = optionalPendingFile.get();
            final String itemId = itemIdFromFile(pendingFile);
            final Path inFlightFile = inFlightDir.resolve(pendingFile.getFileName());

            // Claim the lease before the file exists in in-flight, never after. A
            // concurrent reclaim scan that saw the file first would otherwise take
            // it back from under a consumer that is about to start work on it.
            //
            // Every racer claims, but only the one that wins the move owns the lease
            // and may release it. A loser that released it would erase the winner's
            // claim and hand a live item to the reclaim scan.
            final boolean claimed = activeLeases.add(itemId);

            try {
                // ATOMIC_MOVE silently clobbers its target on the Unix provider, so the
                // FileAlreadyExistsException branch below can never fire there. Without this check the
                // pending copy would destroy a live in-flight message belonging to another lease -
                // orphaning that message's file group - instead of being quarantined. writePending
                // guards the identical move the same way.
                if (Files.exists(inFlightFile)) {
                    throw new FileAlreadyExistsException(
                            inFlightFile.toString(),
                            null,
                            "In-flight message already exists for id '" + itemId + "' on queue '" + name
                            + "' - refusing to overwrite it");
                }

                moveAtomically(pendingFile, inFlightFile);
            } catch (final NoSuchFileException e) {
                if (Files.exists(pendingFile)) {
                    // Not a lost race: the pending file is still there, so what is missing is the
                    // target path or the in-flight directory itself. Continuing would re-select the
                    // same file on the next iteration and spin here forever.
                    if (claimed) {
                        activeLeases.remove(itemId);
                    }
                    throw e;
                }
                // Another local consumer in this JVM/process won the race. Drop our
                // own entry only when there is no in-flight file left for it to
                // protect - that is, when the winner has already finished. Ids are
                // never reused, so nothing can move into that name afterwards.
                if (claimed && !Files.exists(inFlightFile)) {
                    activeLeases.remove(itemId);
                }
                continue;
            } catch (final FileAlreadyExistsException e) {
                // The in-flight file exists and belongs to somebody else's lease. Deliberately do NOT
                // release the lease here, unconditionally or guarded by `claimed`. Both were tried and
                // both fail TestLocalFileGroupQueueLeaseReclaim's concurrency test, because `claimed`
                // records who *added* the id, not who won the move:
                //
                //   B adds the id, then A adds and gets false, then A finds no in-flight file and wins
                //   the move. A now owns live work while B holds claimed == true. B releasing here
                //   strips the only protection A has, and the reclaim scan redelivers A's item.
                //
                // The claim is taken before the move on purpose, so the winner cannot be the claimant.
                // Closing M7 needs the lease to record its owner rather than just the id, which is a
                // design change, not a patch - see the ledger entry for 2026-09-01.
                moveToFailed(pendingFile, "duplicate-pending", e);
                continue;
            }

            final byte[] messageBytes;
            try {
                messageBytes = Files.readAllBytes(inFlightFile);
            } catch (final IOException e) {
                // A failed read says nothing about whether the message is valid, so do not condemn a
                // message that may be perfectly good. Release the lease so the reclaim scan can return
                // it to pending, and let the caller see the failure.
                activeLeases.remove(itemId);
                throw e;
            }

            final FileGroupQueueMessage message;
            try {
                message = codec.fromBytes(messageBytes);
            } catch (final Exception e) {
                // The bytes were read and cannot be decoded, so this message really is unusable.
                activeLeases.remove(itemId);
                moveToFailed(inFlightFile, "invalid-message", e);
                throw new IOException("Unable to read queue message " + inFlightFile, e);
            }

            return Optional.of(new LocalFileGroupQueueItem(itemId, message, inFlightFile));
        }
    }

    @Override
    public void close() {
        // No open resources are held between operations. Persist the id allocator so
        // ids stay monotonic across a clean restart; correctness does not depend on
        // this succeeding, because startup re-derives a safe value by scanning.
        persistSequence();
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

        // Seed before recovering. Recovery re-queues through writePending, which allocates an id, so
        // an unseeded allocator would hand out ids from 1 and collide with whatever is already in
        // pending/ - and the collision is swallowed as a failed requeue, quietly dropping back to a
        // move that does not count the attempt. That is the whole of H9, defeated in the common case
        // of a restart with a non-empty queue. seedSequence reads in-flight/ too, so the ids it
        // establishes already account for the messages recovery is about to move.
        seedSequence();
        recoverInFlightMessages();
    }

    /**
     * Seed the id allocator from the greater of the persisted counter and the highest
     * id present in any of the queue directories.
     * <p>
     * The persisted counter alone is not trustworthy - it can be lost, truncated or
     * restored out of step with the queue contents - and reusing an id would silently
     * overwrite a queued message. Scanning the directories makes the allocator correct
     * regardless of the counter's state; the counter merely keeps ids monotonic across
     * restarts once a queue has drained.
     * </p>
     */
    private void seedSequence() throws IOException {
        long highest = readPersistedSequence();

        for (final Path dir : new Path[]{pendingDir, inFlightDir, failedDir}) {
            highest = Math.max(highest, highestIdIn(dir));
        }

        sequence.set(highest);
    }

    private long readPersistedSequence() {
        try {
            final String value = Files.readString(sequenceFile, StandardCharsets.UTF_8).trim();
            return value.isEmpty()
                    ? 0L
                    : Long.parseLong(value);
        } catch (final IOException | NumberFormatException e) {
            // A missing or corrupt counter is recoverable - the directory scan below
            // establishes a safe floor on its own.
            return 0L;
        }
    }

    private static long highestIdIn(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(LocalFileGroupQueue::isMessageFile)
                    .mapToLong(LocalFileGroupQueue::idFromFileOrZero)
                    .max()
                    .orElse(0L);
        }
    }

    private static long idFromFileOrZero(final Path file) {
        try {
            return Long.parseLong(itemIdFromFile(file));
        } catch (final NumberFormatException e) {
            // Files moved to failed/ carry a suffix and are not plain ids. They can
            // never be re-published under their original name, so ignore them.
            return 0L;
        }
    }

    private void persistSequence() {
        try {
            Files.writeString(
                    sequenceFile,
                    sequence.get() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (final IOException e) {
            // Best effort only - the next startup re-derives a safe value by scanning.
            LOGGER.debug("Unable to persist queue sequence for '{}': {}", name, e.getMessage(), e);
        }
    }

    /**
     * Scan for abandoned leases, at most once per configured interval.
     *
     * @return How many messages were reclaimed, or zero if the scan was skipped.
     */
    private int maybeReclaimAbandonedLeases() {
        final long nowMs = System.currentTimeMillis();
        final long lastMs = lastAbandonedLeaseScanMs.get();

        if (nowMs - lastMs < abandonedLeaseScanInterval.toMillis()) {
            return 0;
        }
        // The timestamp CAS only decides which thread may START a scan at this instant; it does not
        // stop a second scan beginning while the first is still running, and with an interval of zero
        // every poll passes the check above. Two concurrent scans are not safe: reclaimAbandonedLeases
        // tests activeLeases and then moves the in-flight file, and those are not atomic together, so
        // the second scan can return a live consumer's item to pending and hand the same file group to
        // two consumers at once. Serialise the scan itself.
        if (!scanInProgress.compareAndSet(false, true)) {
            return 0;
        }

        try {
            final int reclaimed = reclaimAbandonedLeases();
            if (reclaimed > 0) {
                LOGGER.warn(() -> LogUtil.message(
                        "Queue {} reclaimed {} abandoned lease(s) - a consumer took these items " +
                        "but never acknowledged or failed them",
                        name,
                        reclaimed));
            }
            return reclaimed;
        } catch (final IOException e) {
            // Reclaiming is opportunistic. Failing to do it now costs a delay, not
            // correctness, and the next empty poll will try again.
            LOGGER.debug(() -> LogUtil.message(
                    "Queue {} could not scan for abandoned leases", name), e);
            return 0;
        } finally {
            // Stamp on completion, not on entry, so the interval measures the gap BETWEEN scans.
            lastAbandonedLeaseScanMs.set(System.currentTimeMillis());
            scanInProgress.set(false);
        }
    }

    /**
     * Return in-flight messages held by no live consumer to the pending queue.
     * <p>
     * Before this existed, an item whose {@code acknowledge()} or {@code fail()}
     * threw stayed in {@code in-flight} until the process restarted, because
     * {@link #recoverInFlightMessages()} only runs at construction. The work was
     * not lost, but it stopped, and nothing in the process would ever start it
     * again - an operator had to notice and restart the proxy. SQS and Kafka do
     * not have the problem because a visibility timeout or a rebalance redelivers
     * the message on its own.
     * </p>
     * <p>
     * This is not a visibility timeout. It reclaims an in-flight file only when
     * no live item in this process holds its lease, which is exact rather than a
     * guess about elapsed time, so it can never take work away from a consumer
     * that is merely slow.
     * </p>
     *
     * @return How many messages were returned to pending or quarantined.
     */
    int reclaimAbandonedLeases() throws IOException {
        int reclaimed = 0;

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inFlightDir,
                "*" + MESSAGE_FILE_EXTENSION)) {

            for (final Path inFlightFile : stream) {
                if (activeLeases.contains(itemIdFromFile(inFlightFile))) {
                    continue;
                }

                final Path pendingFile = pendingDir.resolve(inFlightFile.getFileName());
                try {
                    if (Files.exists(pendingFile)) {
                        moveToFailed(inFlightFile, "abandoned-duplicate", null);
                    } else if (!requeueWithIncrementedAttempts(inFlightFile)) {
                        // Could not read or rewrite the message, so fall back to returning it as-is
                        // rather than losing it. It keeps its old attempt count.
                        moveAtomically(inFlightFile, pendingFile);
                        // requeueWithIncrementedAttempts signals via writePending; this path must too,
                        // or a reclaimed item would sit in pending until the next waiter times out.
                        signalWork();
                    }
                    reclaimed++;
                } catch (final NoSuchFileException e) {
                    // The item completed between the scan and the move.
                    LOGGER.debug(() -> LogUtil.message(
                            "Queue {} lost a race reclaiming {}", name, inFlightFile), e);
                }
            }
        }

        return reclaimed;
    }

    /**
     * Return an abandoned in-flight message to pending with its delivery-attempt count incremented, and
     * quarantine it once the bound is reached.
     * <p>
     * Without the increment an item that abandons its own lease - one killed by an {@link Error}, which
     * the worker does not catch - cycles abandon → reclaim → abandon indefinitely, re-running its side
     * effects each time and never reaching {@code maxDeliveryAttempts}. That was survivable while the
     * scan only ran on an empty poll; it is not now the scan runs on every poll.
     * </p>
     *
     * @return true if the message was re-queued or quarantined here, false if the caller should fall
     * back to a plain move.
     */
    private boolean requeueWithIncrementedAttempts(final Path inFlightFile) {
        try {
            final FileGroupQueueMessage message = codec.fromBytes(Files.readAllBytes(inFlightFile));
            final int attempts = deliveryAttempts(message) + 1;
            if (attempts >= maxDeliveryAttempts) {
                moveToFailed(inFlightFile, "max-delivery-attempts", null);
            } else {
                writePending(withDeliveryAttempts(message, attempts));
                Files.deleteIfExists(inFlightFile);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Queue {} could not increment delivery attempts for {}", name, inFlightFile), e);
            return false;
        }
    }

    /**
     * @return Ids currently leased to a live consumer in this process.
     */
    int getActiveLeaseCount() {
        return activeLeases.size();
    }

    private void recoverInFlightMessages() throws IOException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inFlightDir, "*" + MESSAGE_FILE_EXTENSION)) {
            for (final Path inFlightFile : stream) {
                final Path pendingFile = pendingDir.resolve(inFlightFile.getFileName());
                if (Files.exists(pendingFile)) {
                    moveToFailed(inFlightFile, "recovered-duplicate", null);
                } else if (!requeueWithIncrementedAttempts(inFlightFile)) {
                    // Could not read or rewrite the message, so return it as-is rather than lose it.
                    // It keeps its old attempt count, as on the reclaim path.
                    moveAtomically(inFlightFile, pendingFile);
                }
            }
        }
    }

    /**
     * Allocate the next item id.
     * <p>
     * This was previously guarded by a {@link java.nio.channels.FileLock} on
     * {@code sequence.txt}. File locks are held on behalf of the whole JVM rather
     * than per thread, so a second thread publishing concurrently to the same queue
     * hit {@link java.nio.channels.OverlappingFileLockException} and its publish
     * failed. Allocation is now an atomic increment of a counter seeded from disk at
     * construction, which is both correct across threads and free of I/O.
     * </p>
     */
    private long allocateSequence() {
        return sequence.incrementAndGet();
    }

    /**
     * Write and flush to stable storage before the file is made visible in
     * {@code pending/} by the subsequent atomic move.
     */
    private static void writeDurably(final Path file, final byte[] content) throws IOException {
        try (final FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(content));
            channel.force(true);
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
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }

            Files.deleteIfExists(inFlightFile);
            completed = true;
        }

        /**
         * Return this message to the queue, or quarantine it if it has been tried
         * too many times.
         * <p>
         * The message is re-queued under a <strong>new</strong> id, at the back.
         * Returning it under its original id put it back at the head, because
         * {@code findNextPendingFile()} always takes the lowest id - so a message
         * that kept failing was handed straight back out, and every message behind
         * it waited. One message the pipeline could not process was enough to stop
         * a queue completely.
         * </p>
         * <p>
         * Re-queuing needs a bound to go with it. At-least-once delivery means a
         * message can legitimately reference a file group that an earlier duplicate
         * already consumed; such a message can never succeed, and without a limit it
         * would circulate forever. After {@code maxDeliveryAttempts} it goes to
         * {@code failed/} with the last error beside it, which is a quarantine an
         * operator can inspect and replay - not a deletion.
         * </p>
         */
        @Override
        public void fail(final Throwable error) throws IOException {
            if (completed) {
                return;
            }

            if (!Files.exists(inFlightFile)) {
                completed = true;
                return;
            }

            final int attempts = deliveryAttempts(message) + 1;

            if (attempts >= maxDeliveryAttempts) {
                LOGGER.error(() -> LogUtil.message(
                        "Queue {} quarantining message {} (file group {}) after {} delivery attempts",
                        name,
                        message.messageId(),
                        message.fileGroupId(),
                        attempts));
                moveToFailed(inFlightFile, "max-delivery-attempts", error);
                completed = true;
                return;
            }

            // Write the replacement before removing the original: a crash in between
            // costs a duplicate, which the pipeline tolerates, rather than the loss
            // that the other order would risk.
            writePending(withDeliveryAttempts(message, attempts));
            Files.deleteIfExists(inFlightFile);

            completed = true;
        }

        @Override
        public void close() {
            // Callers must still acknowledge() or fail(Throwable); closing is not a
            // substitute for either. Releasing the lease here is what lets the queue
            // reclaim an item whose acknowledge() or fail() threw - the worker logs
            // and rethrows in that case, leaving the message in in-flight with
            // nobody left to finish it.
            activeLeases.remove(itemId);
        }
    }
}
