/*
 * Copyright 2025 Crown Copyright
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
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.StripedLock;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringIdUtil;
import stroom.util.time.StroomDuration;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

@Singleton
public class MergeProcessor {

    public static final String MERGE_TASK_NAME = "Plan B Merge Processor";
    public static final String MAINTAIN_TASK_NAME = "Plan B Maintenance Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final Map<String, DirQueue> mergeQueues = new ConcurrentHashMap<>();
    // Serialises creation of a given queue without holding a lock on the map while we do it.
    private final StripedLock queueCreationLocks = new StripedLock();
    private final Map<String, CompletableFuture<Void>> mergeConsumers = new ConcurrentHashMap<>();
    private final SequentialFileStore receiveStore;
    private final Path mergingDir;
    private final Path unzipDir;
    private final AtomicLong unzipSequenceId = new AtomicLong();
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private final Executor executor;
    private final Provider<PlanBConfig> configProvider;
    private final AtomicBoolean resumedQueues = new AtomicBoolean();
    private volatile boolean merging;

    @Inject
    public MergeProcessor(final StatePaths statePaths,
                          final SecurityContext securityContext,
                          final TaskContextFactory taskContextFactory,
                          final ShardManager shardManager,
                          final ExecutorProvider executorProvider,
                          final Provider<PlanBConfig> configProvider) {
        this.receiveStore = new SequentialFileStore(statePaths.getStagingDir());
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.shardManager = shardManager;
        this.executor = executorProvider.get();
        this.configProvider = configProvider;

        // The contents of the merging dir MUST survive a restart. Data is moved here from the staging store
        // when it is queued for merge and the staged zip is then deleted, so once queued this is the only
        // copy of any data that has not yet been merged. merge() recreates the queues from this dir so that
        // data left behind by the last run, e.g. due to shutdown part way through merging, is merged when
        // the merge job next runs. See gh-5696.
        mergingDir = statePaths.getMergingDir();
        FileUtil.ensureDirExists(mergingDir);

        // The unzip dir is transient. Anything in it still exists as a zip in the staging store, as staged
        // zips are only deleted after their contents have been moved to the merging dir, so it is safe to
        // delete anything left behind by the last run.
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
        // One shot recreation of dir queues for all doc dirs currently in the merging dir, resuming merges
        // for data that was queued but not merged when the process last stopped. After boot, new queue dirs
        // are only ever created via getOrCreateDirQueue so there is no point re-listing. See gh-5696.
        if (resumedQueues.compareAndSet(false, true)) {
            try (final Stream<Path> stream = Files.list(mergingDir)) {
                stream.forEach(path -> {
                    final String docUuid = path.getFileName().toString();
                    try {
                        getOrCreateDirQueue(docUuid);
                    } catch (final RuntimeException e) {
                        // Don't let one bad queue dir stop the other queues from being processed.
                        LOGGER.error(e::getMessage, e);
                    }
                });
            } catch (final IOException e) {
                // We couldn't list the merging dir at all, so try again on the next run rather than leave
                // any queued data unmerged until the next boot.
                LOGGER.error(e::getMessage, e);
                resumedQueues.set(false);
            }
        }

        if (!merging) {
            synchronized (this) {
                if (!merging) {
                    merging = true;
                    CompletableFuture.runAsync(() -> {
                        try {
                            unzipPartFiles();
                        } finally {
                            merging = false;
                        }
                    }, executor);
                }
            }
        }

        // Restart merge processing for any queue whose consumer has stopped, e.g. because it was interrupted
        // by task termination but the system was not actually shut down. This must happen outside the merging
        // guard as the guard stays closed for as long as the unzip loop is running. It is cheap as it only
        // inspects the in-memory queue map.
        mergeQueues.forEach(this::startQueueConsumer);
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
                taskContextFactory.context(MAINTAIN_TASK_NAME, taskContext -> {
                    shardManager.condenseAll(taskContext);
                    deleteOldMergeStatus();
                }).run());
    }

    /**
     * Prune old merge status records from additive store shards. A record may only be pruned once no
     * replayable copy of its source can still exist, so each doc must pass the quiescence test at the time
     * of pruning. The age retention guards the residual race between the test and the prune.
     */
    private void deleteOldMergeStatus() {
        final StroomDuration retention = NullSafe.getOrElse(
                configProvider.get(),
                PlanBConfig::getMergeStatusRetention,
                StroomDuration.ofDays(30));
        final Instant deleteBefore = Instant.now().minus(retention.getDuration());
        shardManager.deleteOldMergeStatus(this::isQuiescent, deleteBefore);
    }

    /**
     * Determine whether any replayable copy of a merge source could still exist for a doc. Replay comes
     * from staged zips (re-unzipped on restart if their delete never happened) or from dirs on the doc's
     * merge queue (re-consumed on restart). Sender retries of unacknowledged parts arrive through staging
     * so are covered by the same test.
     */
    private boolean isQuiescent(final String docUuid) {
        try {
            // The staging store must be fully drained.
            if (receiveStore.getMinStoreId() != -1) {
                return false;
            }

            // No dirs may remain on disk in the doc's merge queue. This must be a filesystem check rather
            // than asking the in-memory queue: a dir whose merge failed is skipped past by the queue
            // consumer, so the queue looks empty while a replayable copy remains on disk for the next boot.
            final Path uuidDir = mergingDir.resolve(docUuid);
            return !Files.isDirectory(uuidDir) || DirUtil.getMaxDirId(uuidDir) == 0;
        } catch (final RuntimeException e) {
            // If in doubt, don't prune.
            LOGGER.debug(e::getMessage, e);
            return false;
        }
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
            } catch (final UncheckedInterruptedException e) {
                // Interrupted, e.g. at shutdown. The zip has not been deleted so it will be processed when
                // the staging store is next read.
                LOGGER.debug(e::getMessage, e);
                throw e;
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
        } catch (final UncheckedInterruptedException e) {
            // Interrupted, e.g. at shutdown. The zip is only deleted once all dirs have been moved to merge
            // queues, so it is safe to stop here and leave everything to be processed when the staging store
            // is next read.
            LOGGER.debug(e::getMessage, e);
            throw e;
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    /**
     * Package private so tests can drive queue creation directly.
     */
    DirQueue getOrCreateDirQueue(final String docUuid) {
        // docUuid is a directory name taken from data received from another node; it must not be able to
        // escape the merging directory when resolved as a single path segment.
        PathSegmentUtil.requireSafeSegment(docUuid);
        DirQueue dirQueue = mergeQueues.get(docUuid);
        if (dirQueue == null) {
            dirQueue = createDirQueue(docUuid);
        }
        // Make sure this queue is being consumed.
        startQueueConsumer(docUuid, dirQueue);
        return dirQueue;
    }

    /**
     * Deliberately not {@link ConcurrentHashMap#computeIfAbsent}: constructing a {@link DirQueue}
     * creates its dir and then recursively scans it to find the min and max ids, which on a queue
     * with a large id gap can walk a lot of directories. computeIfAbsent would do all of that
     * while synchronized on the key's bin, making every other queue in that bin wait for it.
     * Creation is serialised by a striped lock outside the map instead. See gh-5689.
     */
    private DirQueue createDirQueue(final String docUuid) {
        final Lock lock = queueCreationLocks.getLockForKey(docUuid);
        lock.lock();
        try {
            // Another thread may have created it while we were waiting for the lock.
            final DirQueue existing = mergeQueues.get(docUuid);
            if (existing != null) {
                return existing;
            }

            final DirQueue dirQueue;
            try {
                final Path uuidDir = mergingDir.resolve(docUuid);
                Files.createDirectories(uuidDir);
                dirQueue = new DirQueue(uuidDir, docUuid);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            mergeQueues.put(docUuid, dirQueue);
            return dirQueue;
        } finally {
            lock.unlock();
        }
    }

    private void startQueueConsumer(final String docUuid,
                                    final DirQueue dirQueue) {
        // Compute is atomic per key so we will never start more than one consumer for a queue.
        mergeConsumers.compute(docUuid, (k, future) -> {
            if (future == null || future.isDone()) {
                return CompletableFuture.runAsync(() -> mergeStore(dirQueue, docUuid), executor);
            }
            return future;
        });
    }

    private void mergeStore(final DirQueue dirQueue,
                            final String uuid) {
        securityContext.asProcessingUser(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Wait until new data is available.
                    try (final Dir dir = dirQueue.next()) {
                        mergeDir(dir.getPath(), uuid);

                        // If synchronisation is happening on merge then let the parent process know we finished
                        // merging this dir.
                        if (dir.getCountDownLatch() != null) {
                            dir.getCountDownLatch().countDown();
                        }
                    }
                }
            } catch (final UncheckedInterruptedException e) {
                // This thread is being terminated, e.g. at shutdown. Stop consuming from the queue. Any dirs
                // still on the queue remain on disk and will be merged when the queue is next consumed.
                LOGGER.debug(e::getMessage, e);
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
        } catch (final UncheckedInterruptedException e) {
            // The merge was interrupted, e.g. at shutdown. Leave the dir in place so the merge can be rerun,
            // and rethrow so that the caller stops rather than moving on to the next dir. The task context
            // wrapper clears the thread interrupt flag when the task above completes, so this exception is
            // the only remaining signal that we are being terminated. See gh-5696.
            LOGGER.debug(e::getMessage, e);
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
