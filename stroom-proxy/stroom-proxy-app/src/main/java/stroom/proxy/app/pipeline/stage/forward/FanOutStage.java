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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Several destinations are several deliveries of one group, each with its own claim
 * ({@code designs/stages/forward.md} F7, §4.4). This processor consumes the forward stage's input
 * and, for each destination, copies the group into that destination's store and publishes to that
 * destination's queue, then deletes the input. Each destination's {@link ForwardStage} then
 * forwards its own copy with its own retries and its own give-up, so one destination being down
 * never delays, duplicates or gives up on another's delivery.
 * <p>
 * A crash part-way re-copies to every destination on redelivery, which duplicates to the ones
 * already done: the accepted direction.
 * </p>
 */
public final class FanOutStage implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FanOutStage.class);

    /** Carried on each copy's message: which destination the copy is for. */
    public static final String DESTINATION_ATTRIBUTE = "forwardDestination";

    private static final String NAME_PREFIX = "forward-";

    private final FileStoreRegistry stores;
    private final List<Target> targets;
    private final String producerId;

    public FanOutStage(final FileStoreRegistry stores,
                       final List<Target> targets,
                       final String producerId) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        if (this.targets.size() < 2) {
            throw new IllegalArgumentException("Fan-out needs at least two destinations, got " + this.targets);
        }
    }

    /**
     * The pipeline queue a destination's copies are published to. Stated in the pipeline block
     * like every other queue, so that in shared mode it is shared.
     */
    public static String queueNameFor(final String destinationName) {
        return NAME_PREFIX + destinationName;
    }

    /**
     * The pipeline file store a destination's copies are written to.
     */
    public static String storeNameFor(final String destinationName) {
        return NAME_PREFIX + destinationName;
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        final FileGroupQueueMessage message = Objects.requireNonNull(item.getMessage(), "item.message");
        final Path sourceDir = stores.resolve(message);
        final FileGroup source = new FileGroup(sourceDir);
        source.requireComplete("Fan-out input '" + message.messageId() + "'");

        for (final Target target : targets) {
            final FileStoreLocation location;
            try (final FileStoreWrite write = target.store().newWrite()) {
                final FileGroup copy = new FileGroup(write.getPath());
                Files.copy(source.getZip(), copy.getZip(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(source.getEntries(), copy.getEntries(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(source.getMeta(), copy.getMeta(), StandardCopyOption.REPLACE_EXISTING);
                location = write.commit();
            }
            final FileGroupQueueMessage outMessage = FileGroupQueueMessage.create(
                    location,
                    message.feed(),
                    message.type(),
                    PipelineStageName.FORWARD.getConfigName(),
                    producerId,
                    message.traceId(),
                    Map.of(DESTINATION_ATTRIBUTE, target.destinationName()));
            target.queue().publish(outMessage);
            LOGGER.debug(() -> LogUtil.message(
                    "Copied {} for '{}' as {} on {}",
                    message.messageId(), target.destinationName(), outMessage.messageId(), target.queue().getName()));
        }

        // Housekeeping after the durable step: every destination has its copy and its message. A
        // failure here is logged and the input message still acknowledged, since throwing would
        // copy the group to every destination again on each redelivery; an input no message names
        // is reclaimed by the store's sweep, or the start-up sweep in local mode.
        try {
            stores.requireFileStore(message.fileStoreLocation().storeName()).delete(message.fileStoreLocation());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Fanned out {} (feed {}) but failed to delete its input at {}: {}. The message is acknowledged; "
                    + "the group is disk left behind, reclaimed by the store's sweep, rather than data at risk.",
                    message.messageId(), message.feed(), message.fileStoreLocation().uri(),
                    LogUtil.exceptionMessage(e)), e);
            return;
        }
        // The path the store lent: gone with the location on a filesystem store; a per-resolve
        // download on an object store, which is ours to remove.
        if (Files.exists(sourceDir) && !FileUtil.deleteDir(sourceDir)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete the resolved copy of fan-out input {} at {}. It is disk left behind, "
                    + "cleared at the next start, rather than data at risk.", message.messageId(), sourceDir));
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * One destination's store and queue.
     */
    public record Target(String destinationName, FileStore store, FileGroupQueue queue) {

        public Target {
            Objects.requireNonNull(destinationName, "destinationName");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(queue, "queue");
        }
    }
}
