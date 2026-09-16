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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local-mode start-up: the one moment the file stores and the queues can be compared without a
 * race, because this process is the only writer and nothing is running yet. Every committed group
 * that no queue message names is an orphan and is deleted; every empty numbering directory goes with
 * it; and each store then re-establishes its counter from what is left.
 * <p>
 * This is the only cleanup a local store ever gets. Nothing runs on a timer.
 * </p>
 */
public final class LocalModeStartUp {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalModeStartUp.class);

    private LocalModeStartUp() {
    }

    /**
     * Sweep every local store in the configuration against every local queue in it. Queues no stage
     * in this process consumes are read too: a message on them still names live data.
     * <p>
     * If any queue message cannot be read the sweep is skipped, with a warning. A group whose message
     * is unreadable would otherwise be deleted as an orphan, and orphans are space, not data.
     * </p>
     */
    public static void sweepOrphans(final ProxyPipelineConfig pipelineConfig,
                                    final FileGroupQueueFactory queueFactory,
                                    final FileStoreFactory fileStoreFactory) {
        final List<FileGroupQueue> queues = new ArrayList<>();
        pipelineConfig.getQueues().keySet().forEach(queueName -> queues.add(queueFactory.getQueue(queueName)));
        final List<FileStore> stores = new ArrayList<>();
        pipelineConfig.getFileStores().keySet().forEach(storeName ->
                stores.add(fileStoreFactory.getFileStore(storeName)));
        sweepOrphans(queues, stores);
    }

    public static void sweepOrphans(final Collection<FileGroupQueue> queues,
                                    final Collection<FileStore> stores) {
        final Set<FileStoreLocation> live = new HashSet<>();
        for (final FileGroupQueue queue : queues) {
            if (queue instanceof final LocalFileGroupQueue localQueue) {
                try {
                    live.addAll(localQueue.readAllLocations());
                } catch (final IOException e) {
                    LOGGER.warn(() -> LogUtil.message(
                            "Not sweeping orphaned file groups at start-up: queue {} has a message that cannot "
                            + "be read, and the group it names would be deleted. {}",
                            queue.getName(), e.getMessage()));
                    return;
                }
            }
        }

        for (final FileStore store : stores) {
            if (store instanceof final FilesystemFileStore filesystemStore && !filesystemStore.getType().isShared()) {
                try {
                    final int deleted = filesystemStore.deleteAllExcept(live);
                    LOGGER.info(() -> LogUtil.message(
                            "Start-up sweep of file store {}: {} orphaned file group(s) deleted, {} live",
                            store.getName(), deleted, live.size()));
                } catch (final IOException e) {
                    LOGGER.warn(() -> LogUtil.message(
                            "Start-up sweep of file store {} failed: {}", store.getName(), e.getMessage()), e);
                }
            }
        }
    }
}
