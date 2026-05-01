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

package stroom.proxy.app.pipeline;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

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
        this.receiveStore = Objects.requireNonNull(receiveStore, "receiveStore");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.splitZipQueue = splitZipQueue; // Nullable — split-zip is optional.
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");

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
        }
    }

    /**
     * Determine whether the received file group should go to the split-zip
     * queue or the primary output queue.
     * <p>
     * For now, all received files go to the primary output queue. Future
     * logic could inspect the file group to determine if it needs splitting
     * (e.g. multi-entry zips) and route to {@code splitZipQueue} instead.
     * </p>
     */
    private FileGroupQueue resolveTargetQueue(final Path receivedDir) {
        // TODO: Add split-zip routing logic based on file group content.
        //       For now, always use the primary output queue.
        return outputQueue;
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
