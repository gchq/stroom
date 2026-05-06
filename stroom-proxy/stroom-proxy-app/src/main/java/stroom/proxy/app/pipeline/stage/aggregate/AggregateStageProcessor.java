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

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Queue item processor for the aggregate pipeline stage.
 * <p>
 * This is a thin bridge that resolves the input queue message to a source
 * file-group directory and delegates to an {@link AggregateFunction} for
 * the actual merge/aggregation logic. In production the function wraps
 * the existing {@code Aggregator.addDir(Path)} method.
 * </p>
 * <p>
 * The aggregate stage takes pre-aggregated file groups (each containing
 * one or more per-feed numbered subdirectories), merges multiple
 * zip files into a single output zip, combines common meta headers,
 * and passes the merged result to its destination callback
 * ({@link AggregateClosePublisher}).
 * </p>
 * <p>
 * This processor follows the same contract as {@link ForwardStageProcessor}:
 * it does not call {@link FileGroupQueueItem#acknowledge()} or
 * {@link FileGroupQueueItem#fail(Throwable)} — the enclosing
 * {@link FileGroupQueueWorker} owns acknowledgement.
 * </p>
 */
public class AggregateStageProcessor implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateStageProcessor.class);

    private final FileStoreRegistry fileStoreRegistry;
    private final AggregateFunction aggregateFunction;

    /**
     * Functional interface for the aggregation/merge logic.
     * <p>
     * In production this wraps {@code Aggregator.addDir(Path)}.
     * In tests a simple stub can be provided.
     * </p>
     */
    @FunctionalInterface
    public interface AggregateFunction {

        /**
         * Aggregate (merge) the source file-group directory.
         * <p>
         * The implementation should merge multiple per-feed zip files
         * into a single output zip and pass the result to its
         * destination callback.
         * </p>
         *
         * @param sourceDir The resolved source directory containing
         *                  one or more per-feed file-group subdirectories.
         */
        void addDir(Path sourceDir);
    }

    /**
     * @param fileStoreRegistry  Registry for resolving input message locations.
     * @param aggregateFunction  The function that performs the merge
     *                            (typically wraps {@code Aggregator.addDir}).
     */
    public AggregateStageProcessor(final FileStoreRegistry fileStoreRegistry,
                                    final AggregateFunction aggregateFunction) {
        this.fileStoreRegistry = Objects.requireNonNull(fileStoreRegistry, "fileStoreRegistry");
        this.aggregateFunction = Objects.requireNonNull(aggregateFunction, "aggregateFunction");
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
            throw new IOException("Aggregate input message '" + message.messageId()
                                  + "' references '" + sourceDir
                                  + "' but the path is not a directory");
        }

        LOGGER.debug(() -> LogUtil.message(
                "Aggregate stage processing message {} from {} (source: {})",
                message.messageId(),
                message.producingStage(),
                sourceDir));

        // 2. Delegate to the aggregation logic.
        //    The Aggregator handles:
        //    - Counting child directories
        //    - Single-child pass-through (no merge needed)
        //    - Multi-child zip merge with common header combination
        //    - Passing result to destination callback
        aggregateFunction.addDir(sourceDir);

        // 3. Delete the consumed input from the source file store.
        //    At this point the data has been merged into the aggregator's
        //    output. The input file group is no longer needed and must be
        //    deleted to fulfil the ownership-transfer contract.
        final FileStore inputStore = fileStoreRegistry.requireFileStore(
                message.fileStoreLocation().storeName());
        inputStore.delete(message.fileStoreLocation());

        LOGGER.debug(() -> LogUtil.message(
                "Aggregate stage completed processing message {} and deleted input from {}",
                message.messageId(),
                message.fileStoreLocation().storeName()));
    }
}
