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

package stroom.planb.impl.data;

import stroom.docstore.api.DocumentNotFoundException;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringIdUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Singleton
public class MergeProcessor {

    public static final String MERGE_TASK_NAME = "Plan B Merge Processor";
    public static final String MAINTAIN_TASK_NAME = "Plan B Maintenance Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final Map<String, DirQueue> mergeQueues = new ConcurrentHashMap<>();
    private final SequentialFileStore receiveStore;
    private final Path mergingDir;
    private final Path unzipDir;
    private final AtomicLong unzipSequenceId = new AtomicLong();
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private volatile boolean merging;

    @Inject
    public MergeProcessor(final StatePaths statePaths,
                          final SecurityContext securityContext,
                          final TaskContextFactory taskContextFactory,
                          final ShardManager shardManager) {
        this.receiveStore = new SequentialFileStore(statePaths.getStagingDir());
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.shardManager = shardManager;

        mergingDir = statePaths.getMergingDir();
        FileUtil.ensureDirExists(mergingDir);
        if (!FileUtil.deleteContents(mergingDir)) {
            throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(mergingDir));
        }
        unzipDir = statePaths.getUnzipDir();
        FileUtil.ensureDirExists(unzipDir);
        if (!FileUtil.deleteContents(unzipDir)) {
            throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(unzipDir));
        }
    }

    public void add(final FileDescriptor fileDescriptor,
                    final Path file,
                    final boolean synchroniseMerge) throws IOException {
        final FileInfo fileInfo = fileDescriptor.getInfo(file);
        if (synchroniseMerge) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            LOGGER.debug(() -> "Plan B adding part for synchronous merge : " + fileInfo);
            receiveStore.add(fileDescriptor, file, countDownLatch);
            try {
                countDownLatch.await();
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();
            }
        } else {
            LOGGER.debug(() -> "Plan B adding part for merge : " + fileInfo);
            receiveStore.add(fileDescriptor, file, null);
        }
    }

    public void merge() {
        if (!merging) {
            synchronized (this) {
                if (!merging) {
                    merging = true;

                    // Start merge processing for all existing dir queues.
                    try (final Stream<Path> stream = Files.list(mergingDir)) {
                        stream.forEach(path -> {
                            final String docUuid = path.getFileName().toString();
                            getOrCreateDirQueue(docUuid);
                        });
                    } catch (final IOException e) {
                        LOGGER.error(e::getMessage, e);
                    }

                    CompletableFuture.runAsync(() -> {
                        try {
                            unzipPartFiles();
                        } finally {
                            merging = false;
                        }
                    });
                }
            }
        }
    }

    private void unzipPartFiles() {
        securityContext.asProcessingUser(() -> {
            final long minStoreId = receiveStore.getMinStoreId();
            final long maxStoreId = receiveStore.getMaxStoreId();
            LOGGER.info(() -> LogUtil.message("Min store id = {}, max store id = {}",
                    minStoreId,
                    maxStoreId));

            long storeId = minStoreId;
            if (storeId == -1) {
                LOGGER.info("Store is empty");
                storeId = 0;
            }

            while (!Thread.currentThread().isInterrupted()) {
                // Wait until new data is available.
                final long currentStoreId = storeId;
                final SequentialFile sequentialFile = receiveStore.awaitNext(currentStoreId);
                taskContextFactory.context(MERGE_TASK_NAME, taskContext -> {
                    taskContext.info(() -> "Decompressing received data: " + currentStoreId);
                    unzipPartFile(sequentialFile);
                }).run();

                // Increment store id.
                storeId++;
            }
        });
    }

    public void maintainShards() {
        securityContext.asProcessingUser(() ->
                taskContextFactory.context(MAINTAIN_TASK_NAME, shardManager::condenseAll).run());
    }

    public void mergeCurrent() {
        long start = receiveStore.getMinStoreId();

        if (start == -1) {
            LOGGER.info("Merge current store is empty");
            start = 0;
        }

        final long end = receiveStore.getMaxStoreId();
        for (long storeId = start; storeId <= end; storeId++) {
            merge(storeId);
        }
    }

    public void merge(final long storeId) {
        // Wait until new data is available.
        final SequentialFile sequentialFile = receiveStore.awaitNext(storeId);
        taskContextFactory.context(MERGE_TASK_NAME, parentContext -> {
            try {
                final Path zipFile = sequentialFile.getZip();
                if (Files.isRegularFile(zipFile)) {
                    final String dirName = StringIdUtil.idToString(unzipSequenceId.incrementAndGet());
                    final Path dir = unzipDir.resolve(dirName);
                    ZipUtil.unzip(zipFile, dir);

                    // We ought to have one or more stores to merge in this part zip file.
                    try (final Stream<Path> stream = Files.list(dir)) {
                        stream.forEach(source -> {
                            final String docUuid = source.getFileName().toString();
                            mergeDir(source, docUuid);
                        });
                    }

                    // Delete unzip dir.
                    FileUtil.deleteDir(dir);

                    // Delete the original zip file.
                    receiveStore.delete(sequentialFile);
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }).run();
    }

    private void unzipPartFile(final SequentialFile sequentialFile) {
        // Create a map to track the max positions of each of the items we add to the processing queue.
        try {
            final Path zipFile = sequentialFile.getZip();
            if (Files.isRegularFile(zipFile)) {
                final String dirName = StringIdUtil.idToString(unzipSequenceId.incrementAndGet());
                final Path dir = unzipDir.resolve(dirName);
                ZipUtil.unzip(zipFile, dir);

                // We ought to have one or more stores to merge in this part zip file.
                final List<Path> dirs = FileUtil.listChildDirs(dir);

                // If the parent process is waiting for merge then create a countdown latch to cover all dirs that need
                // processing.
                final CountDownLatch countDownLatch;
                if (sequentialFile.getCountDownLatch() != null) {
                    countDownLatch = new CountDownLatch(dirs.size());
                } else {
                    countDownLatch = null;
                }

                // Start processing all dirs.
                dirs.forEach(source -> {
                    final String docUuid = source.getFileName().toString();
                    final DirQueue queue = getOrCreateDirQueue(docUuid);
                    queue.add(source, countDownLatch);
                });

                // If the parent process is waiting for merge to complete then wait.
                if (countDownLatch != null) {
                    try {
                        countDownLatch.await();
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        Thread.currentThread().interrupt();
                    }
                    sequentialFile.getCountDownLatch().countDown();
                }

                // Delete unzip dir.
                FileUtil.deleteDir(dir);

                // Delete the original zip file.
                receiveStore.delete(sequentialFile);
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private DirQueue getOrCreateDirQueue(final String docUuid) {
        return mergeQueues.computeIfAbsent(docUuid, k -> {
            try {
                final Path uuidDir = mergingDir.resolve(docUuid);
                Files.createDirectories(uuidDir);
                final DirQueue dirQueue = new DirQueue(uuidDir, docUuid);
                // Start processing this queue.
                CompletableFuture.runAsync(() -> mergeStore(dirQueue, docUuid));
                return dirQueue;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void mergeStore(final DirQueue dirQueue,
                            final String uuid) {
        securityContext.asProcessingUser(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // Wait until new data is available.
                try (final Dir dir = dirQueue.next()) {
                    mergeDir(dir.getPath(), uuid);

                    // If synchronisation is happening on merge then let the parent process know we finished merging
                    // this dir.
                    if (dir.getCountDownLatch() != null) {
                        dir.getCountDownLatch().countDown();
                    }
                }
            }
        });
    }

    private void mergeDir(final Path path,
                          final String uuid) {
        try {
            final Shard shard = shardManager.getShardForDocUuid(uuid);
            final String name = NullSafe.get(shard, Shard::getDoc, PlanBDoc::getName);
            taskContextFactory.context("Merging Plan B Data '" + name + "'", taskContext -> {
                taskContext.info(() -> "Merging data into '" + name + "'");
                shard.merge(path);
                FileUtil.deleteDir(path);
            }).run();
        } catch (final DocumentNotFoundException e) {
            // Expected exception if a doc has been deleted.
            LOGGER.debug(e::getMessage, e);
            FileUtil.deleteDir(path);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
