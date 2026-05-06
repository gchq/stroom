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

package stroom.proxy.app.pipeline.stage.preaggregate;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Queue item processor for the pre-aggregate pipeline stage.
 * <p>
 * This is a thin bridge that resolves the input queue message to a source
 * file-group directory and delegates to a {@link PreAggregateFunction} for
 * the actual stateful aggregation logic. In production the function wraps
 * the existing {@code PreAggregator.addDir(Path)} method.
 * </p>
 * <p>
 * Unlike the split-zip processor, pre-aggregation is inherently stateful:
 * each incoming file group is accumulated into an open aggregate keyed by
 * feed, and aggregates are closed either when size/count thresholds are
 * reached or via periodic age checks. The aggregate close callback
 * (set as the {@code PreAggregator.destination}) is responsible for
 * publishing the closed aggregate to the output queue.
 * </p>
 * <p>
 * This processor follows the same contract as {@link ForwardStageProcessor}:
 * it does not call {@link FileGroupQueueItem#acknowledge()} or
 * {@link FileGroupQueueItem#fail(Throwable)} — the enclosing
 * {@link FileGroupQueueWorker} owns acknowledgement.
 * </p>
 */
public class PreAggregateStageProcessor implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PreAggregateStageProcessor.class);

    private final FileStoreRegistry fileStoreRegistry;
    private final PreAggregateFunction preAggregateFunction;

    /**
     * Functional interface for the stateful pre-aggregation logic.
     * <p>
     * In production this wraps {@code PreAggregator.addDir(Path)}.
     * In tests a simple stub can be provided.
     * </p>
     */
    @FunctionalInterface
    public interface PreAggregateFunction {

        /**
         * Add a source file-group directory to the pre-aggregation state.
         * <p>
         * The implementation may accumulate the input into an open aggregate,
         * split it, or close completed aggregates as a side effect. Aggregate
         * closure triggers the destination callback which publishes to the
         * output queue.
         * </p>
         *
         * @param sourceDir The resolved source file-group directory.
         */
        void addDir(Path sourceDir);
    }

    /**
     * @param fileStoreRegistry     Registry for resolving input message locations.
     * @param preAggregateFunction  The function that performs the stateful
     *                               pre-aggregation (typically wraps
     *                               {@code PreAggregator.addDir}).
     */
    public PreAggregateStageProcessor(final FileStoreRegistry fileStoreRegistry,
                                       final PreAggregateFunction preAggregateFunction) {
        this.fileStoreRegistry = Objects.requireNonNull(fileStoreRegistry, "fileStoreRegistry");
        this.preAggregateFunction = Objects.requireNonNull(preAggregateFunction, "preAggregateFunction");
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
            throw new IOException("Pre-aggregate input message '" + message.messageId()
                                  + "' references '" + sourceDir
                                  + "' but the path is not a directory");
        }

        LOGGER.debug(() -> LogUtil.message(
                "Pre-aggregate stage processing message {} from {} (source: {})",
                message.messageId(),
                message.producingStage(),
                sourceDir));

        // 2. Delegate to the pre-aggregation logic.
        //    The PreAggregator handles:
        //    - Reading the file group's feed key from meta
        //    - Accumulating into open aggregates (with feed-key striped locking)
        //    - Splitting oversized inputs
        //    - Closing completed aggregates via the destination callback
        preAggregateFunction.addDir(sourceDir);

        // 3. Delete the consumed input from the source file store.
        //    At this point the data has been absorbed into the aggregator's
        //    internal state. The input file group is no longer needed and
        //    must be deleted to fulfil the ownership-transfer contract.
        final FileStore inputStore = fileStoreRegistry.requireFileStore(
                message.fileStoreLocation().storeName());
        inputStore.delete(message.fileStoreLocation());

        LOGGER.debug(() -> LogUtil.message(
                "Pre-aggregate stage completed processing message {} and deleted input from {}",
                message.messageId(),
                message.fileStoreLocation().storeName()));
    }
}
