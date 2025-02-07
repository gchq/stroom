/*
 * Copyright 2016 Crown Copyright
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

package stroom.resource.impl;

import stroom.resource.api.ResourceStore;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Store that gives you 1 hour to use your temp file before it deletes it.
 */
@Singleton
public class ResourceStoreImpl implements ResourceStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResourceStoreImpl.class);

    private final TempDirProvider tempDirProvider;
    private final TaskContextFactory taskContextFactory;
    private final Map<String, ResourceItem> currentFiles = new ConcurrentHashMap<>();

    private volatile Instant lastCleanupTime;

    @Inject
    public ResourceStoreImpl(final TempDirProvider tempDirProvider,
                             final TaskContextFactory taskContextFactory) {
        this.tempDirProvider = tempDirProvider;
        this.taskContextFactory = taskContextFactory;
    }

    private Path getTempDir() {
        final Path tempDir = tempDirProvider.get().resolve("resources");
        try {
            Files.createDirectories(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return tempDir;
    }

    void startup() {
        FileUtil.deleteContents(getTempDir());
    }

    void shutdown() {
        FileUtil.deleteContents(getTempDir());
    }

    @Override
    public ResourceKey createTempFile(final String name) {
        final String uuid = UUID.randomUUID().toString();
        final Path path = getTempDir().resolve(uuid);
        final ResourceKey resourceKey = new ResourceKey(uuid, name);
        final ResourceItem resourceItem = new ResourceItem(resourceKey, path, Instant.now());
        currentFiles.put(uuid, resourceItem);
        return resourceKey;
    }

    @Override
    public void deleteTempFile(final ResourceKey resourceKey) {
        final ResourceItem resourceItem = currentFiles.remove(resourceKey.getKey());
        if (resourceItem != null) {
            final Path file = resourceItem.getPath();
            try {
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public Path getTempFile(final ResourceKey resourceKey) {
        // File gone !
        final ResourceItem resourceItem = currentFiles.get(resourceKey.getKey());
        if (resourceItem == null) {
            return null;
        }
        resourceItem.setLastAccessTime(Instant.now());
        return resourceItem.getPath();
    }

    void execute() {
        taskContextFactory.current().info(() -> "Deleting temp files");
        cleanup();
    }

    /**
     * Delete files that haven't been accessed since the last cleanup.
     * This allows us to choose the cleanup frequency.
     */
    private synchronized void cleanup() {
        if (lastCleanupTime != null) {
            // Delete anything that hasn't been accessed since we were last asked to cleanup.
            currentFiles.values().forEach(resourceItem -> {
                try {
                    if (resourceItem.getLastAccessTime().isBefore(lastCleanupTime)) {
                        deleteTempFile(resourceItem.getResourceKey());
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        }
        lastCleanupTime = Instant.now();
    }

    private static class ResourceItem {

        private final ResourceKey resourceKey;
        private final Path path;
        private final Instant createTime;
        private volatile Instant lastAccessTime;

        public ResourceItem(final ResourceKey resourceKey,
                            final Path path,
                            final Instant createTime) {
            this.resourceKey = resourceKey;
            this.path = path;
            this.createTime = createTime;
            this.lastAccessTime = createTime;
        }

        public ResourceKey getResourceKey() {
            return resourceKey;
        }

        public Path getPath() {
            return path;
        }

        public Instant getCreateTime() {
            return createTime;
        }

        public Instant getLastAccessTime() {
            return lastAccessTime;
        }

        public void setLastAccessTime(final Instant lastAccessTime) {
            this.lastAccessTime = lastAccessTime;
        }

        @Override
        public String toString() {
            return "ResourceItem{" +
                    "resourceKey=" + resourceKey +
                    ", path=" + path +
                    ", createTime=" + createTime +
                    ", lastAccessTime=" + lastAccessTime +
                    '}';
        }
    }
}
