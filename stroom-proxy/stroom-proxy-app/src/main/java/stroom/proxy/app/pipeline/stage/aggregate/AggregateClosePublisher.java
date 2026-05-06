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

package stroom.proxy.app.pipeline.stage.aggregate;

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
 * Callback set as the {@code destination} on {@code PreAggregator} (or
 * {@code Aggregator}) to publish closed aggregates to the reference-message
 * queue pipeline.
 * <p>
 * When the pre-aggregator or aggregator closes an aggregate, it calls
 * {@code destination.accept(aggregateDir)}. This publisher copies the
 * aggregate directory into the output {@link FileStore}, publishes a
 * {@link FileGroupQueueMessage} to the output queue, and cleans up
 * the source aggregate directory.
 * </p>
 * <p>
 * This follows the same pattern as {@link ReceiveStagePublisher} but
 * is parameterised by stage name so it can be reused for both
 * pre-aggregate and aggregate close callbacks.
 * </p>
 */
public class AggregateClosePublisher implements Consumer<Path> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateClosePublisher.class);

    private final FileStore outputStore;
    private final FileGroupQueue outputQueue;
    private final PipelineStageName stageName;
    private final String sourceNodeId;

    /**
     * @param outputStore  The output file store for closed aggregates.
     * @param outputQueue  The output queue (e.g. aggregateInput or
     *                     forwardingInput).
     * @param stageName    The pipeline stage name for message provenance
     *                     (e.g. PRE_AGGREGATE or AGGREGATE).
     * @param sourceNodeId The node identifier for queue message provenance.
     */
    public AggregateClosePublisher(final FileStore outputStore,
                                    final FileGroupQueue outputQueue,
                                    final PipelineStageName stageName,
                                    final String sourceNodeId) {
        this.outputStore = Objects.requireNonNull(outputStore, "outputStore");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
    }

    /**
     * Accept a closed aggregate directory and publish it to the output queue.
     *
     * @param aggregateDir The directory containing the closed aggregate
     *                     (one or more file-group subdirectories).
     * @throws UncheckedIOException If the file store write, queue publish,
     * or cleanup fails.
     */
    @Override
    public void accept(final Path aggregateDir) {
        Objects.requireNonNull(aggregateDir, "aggregateDir");

        try {
            // 1. Copy the aggregate directory into the output file store.
            final FileStoreLocation location;
            try (final FileStoreWrite write = outputStore.newWrite()) {
                copyDirectoryContents(aggregateDir, write.getPath());
                location = write.commit();
            }

            // 2. Build and publish the queue message.
            final String fileGroupId = UUID.randomUUID().toString();
            final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                    outputQueue.getName(),
                    fileGroupId,
                    location,
                    stageName.getConfigName(),
                    sourceNodeId,
                    null,
                    Map.of());

            outputQueue.publish(message);

            LOGGER.debug(() -> LogUtil.message(
                    "Published closed aggregate {} to queue {} (stage: {}, store: {})",
                    fileGroupId,
                    outputQueue.getName(),
                    stageName.getConfigName(),
                    location.uri()));

            // 3. Clean up the source aggregate directory.
            //    The PreAggregator/Aggregator has already removed the aggregate
            //    from its state map, so this directory is no longer referenced.
            deleteRecursively(aggregateDir);

        } catch (final IOException e) {
            throw new UncheckedIOException(
                    "Failed to publish closed aggregate from " + aggregateDir, e);
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

        if (Files.isDirectory(path)) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (final Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
