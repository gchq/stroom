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

package stroom.proxy.app.pipeline.stage.receive;

import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Bridge between receive handlers and the reference-message queue pipeline.
 * <p>
 * Receives a temporary directory containing received file-group files
 * (proxy.meta, proxy.zip, proxy.entries), copies them into the receive output
 * {@link FileStore}, publishes a {@link FileGroupQueueMessage} to the
 * configured output queue, and then deletes the temporary receive directory.
 * </p>
 * <p>
 * This class implements {@link Consumer}{@code <Path>} so it can be set as the
 * {@code destination} on {@code SimpleReceiver} or {@code ZipReceiver},
 * replacing the old directory-move-to-DirQueue pattern.
 * </p>
 */
public class ReceiveStagePublisher implements Consumer<Path> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveStagePublisher.class);

    private final FileStore receiveStore;
    private final FileGroupQueue outputQueue;
    private final FileGroupQueue splitZipQueue;
    private final String sourceNodeId;

    /**
     * Bounds how many receives may be writing to the file store and publishing at
     * once. Acquired for the whole of {@link #accept(Path)}, so a receiving thread
     * blocks here rather than piling more concurrent work onto the store, which
     * applies backpressure up the calling (HTTP or scanner) thread.
     * <p>
     * Fair ordering is used so a sustained burst cannot starve an early arrival.
     * </p>
     */
    private final Semaphore receiveSlots;

    /**
     * Create a publisher admitting {@link ReceiveStageThreadsConfig#DEFAULT_MAX_CONCURRENT_RECEIVES}
     * concurrent receives.
     *
     * @param receiveStore The receive stage's output file store.
     * @param outputQueue  The primary output queue (e.g. preAggregateInput or
     *                     forwardingInput).
     * @param splitZipQueue Optional split-zip queue — if non-null, zip files
     *                      that require splitting are published here instead
     *                      of the primary output queue.
     * @param sourceNodeId The node identifier for queue message provenance.
     */
    public ReceiveStagePublisher(final FileStore receiveStore,
                                  final FileGroupQueue outputQueue,
                                  final FileGroupQueue splitZipQueue,
                                  final String sourceNodeId) {
        this(receiveStore,
                outputQueue,
                splitZipQueue,
                sourceNodeId,
                ReceiveStageThreadsConfig.DEFAULT_MAX_CONCURRENT_RECEIVES);
    }

    /**
     * @param receiveStore The receive stage's output file store.
     * @param outputQueue  The primary output queue (e.g. preAggregateInput or
     *                     forwardingInput).
     * @param splitZipQueue Optional split-zip queue — if non-null, zip files
     *                      that require splitting are published here instead
     *                      of the primary output queue.
     * @param sourceNodeId The node identifier for queue message provenance.
     * @param maxConcurrentReceives Maximum number of concurrent receives admitted
     *                              to the file store and output queue. Must be >= 1.
     */
    public ReceiveStagePublisher(final FileStore receiveStore,
                                  final FileGroupQueue outputQueue,
                                  final FileGroupQueue splitZipQueue,
                                  final String sourceNodeId,
                                  final int maxConcurrentReceives) {
        this.receiveStore = Objects.requireNonNull(receiveStore, "receiveStore");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.splitZipQueue = splitZipQueue; // Nullable — split-zip is optional.
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");

        if (maxConcurrentReceives < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrentReceives must be >= 1 but was " + maxConcurrentReceives);
        }
        this.receiveSlots = new Semaphore(maxConcurrentReceives, true);
    }

    /**
     * Accept a temporary receive directory and publish its contents to the
     * reference-message queue pipeline.
     *
     * @param receivedDir The temporary directory containing the received
     *                    file group (proxy.meta, proxy.zip, proxy.entries).
     * @throws UncheckedIOException If the file store write, queue publish,
     * or temp cleanup fails.
     */
    @Override
    public void accept(final Path receivedDir) {
        Objects.requireNonNull(receivedDir, "receivedDir");

        try {
            receiveSlots.acquire();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new InterruptedIOException(
                    "Interrupted waiting for a receive slot for " + receivedDir));
        }

        try {
            // 1. Copy received files into the file store.
            final FileStoreLocation location;
            try (final FileStoreWrite write = receiveStore.newWrite()) {
                copyDirectoryContents(receivedDir, write.getPath());
                location = write.commit();
            }

            // 2. Determine which output queue to publish to.
            final FileGroupQueue targetQueue = resolveTargetQueue(receivedDir);
            final String fileGroupId = UUID.randomUUID().toString();

            // 3. Build and publish the queue message.
            final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                    targetQueue.getName(),
                    fileGroupId,
                    location,
                    PipelineStageName.RECEIVE.getConfigName(),
                    sourceNodeId,
                    null,
                    Map.of());

            targetQueue.publish(message);

            LOGGER.debug(() -> LogUtil.message(
                    "Published receive file group {} to queue {} (store location: {})",
                    fileGroupId,
                    targetQueue.getName(),
                    location.uri()));

            // 4. Clean up the temporary receive directory.
            deleteRecursively(receivedDir);

        } catch (final IOException e) {
            throw new UncheckedIOException(
                    "Failed to publish received file group from " + receivedDir, e);
        } finally {
            receiveSlots.release();
        }
    }

    /**
     * Determine whether the received file group should go to the split-zip
     * queue or the primary output queue.
     * <p>
     * If a split-zip queue is configured and the received file group contains
     * entries from multiple feeds (determined by inspecting
     * {@code proxy.entries}), the file group is routed to the split-zip queue
     * so that {@link SplitZipStageProcessor} can separate the entries by feed.
     * Single-feed file groups go directly to the primary output queue.
     * </p>
     */
    private FileGroupQueue resolveTargetQueue(final Path receivedDir) {
        if (splitZipQueue == null) {
            return outputQueue;
        }

        if (requiresSplitting(receivedDir)) {
            LOGGER.debug(() -> LogUtil.message(
                    "Routing received file group {} to split-zip queue {}",
                    receivedDir,
                    splitZipQueue.getName()));
            return splitZipQueue;
        }

        return outputQueue;
    }

    /**
     * Check whether the received file group contains entries from more than one feed and type. If so
     * it needs splitting before aggregation.
     * <p>
     * The unit counted is the {@link stroom.proxy.repo.FeedKey} - feed <em>and</em> type - because that
     * is what {@link stroom.proxy.app.handler.ZipSplitter} groups by, so this predicate is true exactly
     * when the splitter would produce more than one output.
     * </p>
     * <p>
     * The check reads {@code proxy.entries} and counts distinct feed keys.
     * If the entries file is missing or unreadable, no splitting is assumed.
     * </p>
     * <p>
     * {@code proxy.entries} holds one JSON-serialised {@link ZipEntryGroup} per line. It is read with
     * {@link ZipEntryGroup#read(Path)}, which owns that format. A previous hand-rolled parse split each
     * line on its first colon, which for JSON is always the one after {@code "feedName"} - so every
     * line yielded the same value, the distinct count was always 1, and multi-feed zips were never
     * split.
     * </p>
     */
    private boolean requiresSplitting(final Path receivedDir) {
        final Path entriesFile = receivedDir.resolve("proxy.entries");
        if (!Files.isRegularFile(entriesFile)) {
            return false;
        }

        try {
            final long distinctFeedKeys = ZipEntryGroup.read(entriesFile)
                    .stream()
                    .map(ZipEntryGroup::getFeedKey)
                    .distinct()
                    .limit(2) // Only need to know if > 1.
                    .count();
            return distinctFeedKeys > 1;
        } catch (final RuntimeException e) {
            // ZipEntryGroup.read wraps I/O and parse failures as unchecked.
            LOGGER.warn(() -> LogUtil.message(
                    "Cannot read proxy.entries in {}, assuming no split required",
                    receivedDir), e);
            return false;
        }
    }

    private static void copyDirectoryContents(final Path source,
                                               final Path target) throws IOException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (final Path entry : stream) {
                final Path targetEntry = target.resolve(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(targetEntry);
                    copyDirectoryContents(entry, targetEntry);
                } else {
                    Files.copy(entry, targetEntry, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (final Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
