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

package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import jakarta.inject.Inject;

import java.nio.file.Path;

public class DirQueueFactory {

    private final Path dataDir;
    private final QueueMonitors queueMonitors;
    private final FileStores fileStores;

    @Inject
    public DirQueueFactory(final DataDirProvider dataDirProvider,
                           final QueueMonitors queueMonitors,
                           final FileStores fileStores) {
        this.dataDir = dataDirProvider.get();
        this.queueMonitors = queueMonitors;
        this.fileStores = fileStores;
    }

    /// Creates a queue rooted at `dirName` under the proxy data dir.
    ///
    /// @param fsyncEnabled If true, the parent dir is forced to disk each time an item is added,
    ///                     making the atomic move that commits the item durable.
    public DirQueue create(final String dirName,
                           final int order,
                           final String name,
                           final boolean fsyncEnabled) {
        final Path rootDir = dataDir.resolve(dirName);
        return create(rootDir, order, name, fsyncEnabled);
    }

    /// Creates a queue rooted at `rootDir`.
    ///
    /// @param fsyncEnabled If true, the parent dir is forced to disk each time an item is added,
    ///                     making the atomic move that commits the item durable.
    public DirQueue create(final Path rootDir,
                           final int order,
                           final String name,
                           final boolean fsyncEnabled) {
        return new DirQueue(rootDir, queueMonitors, fileStores, order, name, fsyncEnabled);
    }
}
