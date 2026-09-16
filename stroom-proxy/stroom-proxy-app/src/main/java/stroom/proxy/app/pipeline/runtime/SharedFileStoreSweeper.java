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

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Runs {@link FilesystemFileStore#sweep()} on every shared filesystem store; the assembler
 * registers {@link #sweepAll()} as a housekeeping schedule. Every node runs this; deletes are
 * idempotent, so two nodes sweeping at once is harmless. It exists because a node that left
 * residue on a mount may never start again, so start-up cannot be the mechanism there as it is
 * for a local store.
 * <p>
 * S3 stores are not swept by the proxy; a lifecycle rule the operator configures does that.
 * </p>
 */
public final class SharedFileStoreSweeper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreSweeper.class);

    public static final Duration INTERVAL = Duration.ofHours(1);

    private final List<FilesystemFileStore> stores;

    private SharedFileStoreSweeper(final List<FilesystemFileStore> stores) {
        this.stores = List.copyOf(stores);
    }

    public static SharedFileStoreSweeper forStores(final Collection<FileStore> fileStores) {
        return new SharedFileStoreSweeper(fileStores.stream()
                .filter(store -> store instanceof FilesystemFileStore)
                .map(store -> (FilesystemFileStore) store)
                .filter(store -> store.getType().isShared())
                .toList());
    }

    public boolean hasStores() {
        return !stores.isEmpty();
    }

    /**
     * One pass over every store, each failure logged and contained so the next store still runs.
     */
    public void sweepAll() {
        for (final FilesystemFileStore store : stores) {
            try {
                final int deleted = store.sweep();
                LOGGER.info(() -> LogUtil.message("Swept file store {}: {} item(s) deleted", store.getName(), deleted));
            } catch (final Exception e) {
                LOGGER.error(() -> LogUtil.message(
                        "Sweep of file store {} failed: {}", store.getName(), e.getMessage()), e);
            }
        }
    }
}
