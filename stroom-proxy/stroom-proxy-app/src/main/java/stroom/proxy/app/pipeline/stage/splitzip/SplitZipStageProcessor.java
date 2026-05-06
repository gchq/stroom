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

package stroom.proxy.app.pipeline.stage.splitzip;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Queue item processor for the split-zip pipeline stage.
 * <p>
 * Resolves the input queue message to a source file group, splits
 * multi-entry/multi-feed zips into per-feed file groups, writes each
 * split output to the configured output {@link FileStore}, publishes
 * an onward {@link FileGroupQueueMessage} for each split, and finally
 * deletes the consumed input source from the input file store.
 * </p>
 * <p>
 * This processor follows the same contract as
 * {@link ForwardStageProcessor}: it does not call
 * {@link FileGroupQueueItem#acknowledge()} or
 * {@link FileGroupQueueItem#fail(Throwable)} — the enclosing
 * {@link FileGroupQueueWorker} owns acknowledgement.
 * </p>
 */
public class SplitZipStageProcessor implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SplitZipStageProcessor.class);

    private final FileStoreRegistry fileStoreRegistry;
    private final FileStore outputStore;
    private final FileGroupQueue outputQueue;
    private final String sourceNodeId;
    private final SplitFunction splitFunction;

    /**
     * Functional interface for the actual zip-splitting logic.
     * <p>
     * This allows production code to delegate to the existing
     * {@code ZipSplitter.splitZipByFeed()} static method while tests
     * can supply a simple stub.
     * </p>
     */
    @FunctionalInterface
    public interface SplitFunction {

        /**
         * Split the source file group into one or more output file groups.
         * <p>
         * The implementation should write split output directories as
         * children of {@code outputParentDir}. Each child directory must
         * be a valid proxy file group (proxy.meta, proxy.zip,
         * proxy.entries).
         * </p>
         *
         * @param sourceDir       The resolved source file-group directory.
         * @param outputParentDir A temporary parent directory for split
         *                        outputs.
         * @throws IOException If splitting fails.
         */
        void split(Path sourceDir, Path outputParentDir) throws IOException;
    }

    /**
     * @param fileStoreRegistry Registry for resolving input message locations.
     * @param outputStore       The output file store for split results.
     * @param outputQueue       The output queue for onward messages (e.g.
     *                          preAggregateInput).
     * @param sourceNodeId      Node identifier for message provenance.
     * @param splitFunction     The function that performs the actual zip
     *                          splitting.
     */
    public SplitZipStageProcessor(final FileStoreRegistry fileStoreRegistry,
                                   final FileStore outputStore,
                                   final FileGroupQueue outputQueue,
                                   final String sourceNodeId,
                                   final SplitFunction splitFunction) {
        this.fileStoreRegistry = Objects.requireNonNull(fileStoreRegistry, "fileStoreRegistry");
        this.outputStore = Objects.requireNonNull(outputStore, "outputStore");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        this.splitFunction = Objects.requireNonNull(splitFunction, "splitFunction");
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        Objects.requireNonNull(item, "item");

        final FileGroupQueueMessage message = Objects.requireNonNull(
                item.getMessage(),
                "item.message");

        // 1. Resolve the input message to a source directory.
        final Path sourceDir = fileStoreRegistry.resolve(message);
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Split-zip input message '" + message.messageId()
                                  + "' references '" + sourceDir
                                  + "' but the path is not a directory");
        }

        // 2. Create a temporary directory for split outputs.
        final Path tempSplitDir = Files.createTempDirectory("split-zip-");

        try {
            // 3. Delegate to the split function.
            splitFunction.split(sourceDir, tempSplitDir);

            // 4. For each split output, copy to the output file store
            //    and publish an onward queue message.
            final java.util.concurrent.atomic.AtomicInteger splitCount =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(tempSplitDir)) {
                for (final Path splitDir : stream) {
                    if (!Files.isDirectory(splitDir)) {
                        continue;
                    }

                    // Write to output store.
                    final FileStoreLocation location;
                    try (final FileStoreWrite write = outputStore.newWrite()) {
                        copyDirectoryContents(splitDir, write.getPath());
                        location = write.commit();
                    }

                    // Publish onward message.
                    final String fileGroupId = UUID.randomUUID().toString();
                    final FileGroupQueueMessage outMessage = FileGroupQueueMessage.create(
                            outputQueue.getName(),
                            fileGroupId,
                            location,
                            PipelineStageName.SPLIT_ZIP.getConfigName(),
                            sourceNodeId,
                            message.traceId(),
                            Map.of());

                    outputQueue.publish(outMessage);
                    splitCount.incrementAndGet();

                    LOGGER.debug(() -> LogUtil.message(
                            "Published split output {} to queue {} (from input {})",
                            fileGroupId,
                            outputQueue.getName(),
                            message.messageId()));
                }
            }

            LOGGER.debug(() -> LogUtil.message(
                    "Split-zip stage processed message {} into {} split(s)",
                    message.messageId(),
                    splitCount.get()));

            // 5. Delete the consumed input from the source file store.
            final FileStore inputStore = fileStoreRegistry.requireFileStore(
                    message.fileStoreLocation().storeName());
            inputStore.delete(message.fileStoreLocation());

        } finally {
            // Always clean up the temporary split directory.
            deleteRecursively(tempSplitDir);
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
