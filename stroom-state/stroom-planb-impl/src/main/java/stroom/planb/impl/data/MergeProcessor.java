package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.StringIdUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Singleton
public class MergeProcessor {

    public static final String MERGE_TASK_NAME = "Plan B Merge Processor";
    public static final String MAINTAIN_TASK_NAME = "Plan B Maintenance Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final SequentialFileStore fileStore;
    private final Path mergingDir;
    private final AtomicLong mergingId = new AtomicLong();
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private final ReentrantLock maintenanceLock = new ReentrantLock();
    private final CountLock countLock = new CountLock();
    private volatile boolean merging;

    @Inject
    public MergeProcessor(final SequentialFileStore fileStore,
                          final StatePaths statePaths,
                          final SecurityContext securityContext,
                          final TaskContextFactory taskContextFactory,
                          final ShardManager shardManager) {
        this.fileStore = fileStore;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.shardManager = shardManager;

        mergingDir = statePaths.getMergingDir();
        FileUtil.ensureDirExists(mergingDir);
        if (!FileUtil.deleteContents(mergingDir)) {
            throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(mergingDir));
        }
    }

    public void merge() {
        if (!merging) {
            synchronized (this) {
                if (!merging) {
                    merging = true;
                    CompletableFuture.runAsync(() -> {
                        try {
                            doMerge();
                        } finally {
                            merging = false;
                        }
                    });
                }
            }
        }
    }

    private void doMerge() {
        securityContext.asProcessingUser(() -> {
            final long minStoreId = fileStore.getMinStoreId();
            final long maxStoreId = fileStore.getMaxStoreId();
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
                final SequentialFile sequentialFile = fileStore.awaitNext(currentStoreId);
                maintenanceLock.lock();
                try {
                    taskContextFactory.context(MERGE_TASK_NAME, taskContext -> {
                        taskContext.info(() -> "Merging data: " + currentStoreId);
                        merge(sequentialFile);
                    }).run();
                } finally {
                    maintenanceLock.unlock();

                    // Notify anybody that cares how far the merge process has got.
                    countLock.setCount(currentStoreId);

                    // Increment store id.
                    storeId++;
                }
            }
        });
    }

    public void maintainShards() {
        securityContext.asProcessingUser(() -> {
            maintenanceLock.lock();
            try {
                shardManager.condenseAll();
            } finally {
                maintenanceLock.unlock();
            }
        });
    }

    public void mergeCurrent() {
        final long start = fileStore.getMinStoreId();
        final long end = fileStore.getMaxStoreId();
        for (long storeId = start; storeId <= end; storeId++) {
            // Wait until new data is available.
            final SequentialFile sequentialFile = fileStore.awaitNext(storeId);
            merge(sequentialFile);
        }
    }

    public void merge(final long storeId) {
        // Wait until new data is available.
        final SequentialFile sequentialFile = fileStore.awaitNext(storeId);
        merge(sequentialFile);
    }

    private void merge(final SequentialFile sequentialFile) {
        try {
            final Path zipFile = sequentialFile.getZip();
            if (Files.isRegularFile(zipFile)) {
                final String mergingDirName = StringIdUtil.idToString(mergingId.incrementAndGet());
                final Path dir = mergingDir.resolve(mergingDirName);
                ZipUtil.unzip(zipFile, dir);

                // We ought to have one or more stores to merge.
                try (final Stream<Path> stream = Files.list(dir)) {
                    stream.forEach(source -> {
                        try {
                            // Merge source.
                            shardManager.merge(source);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }

                // Delete dir.
                FileUtil.deleteDir(dir);

                // Delete the original zip file.
                sequentialFile.delete();
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public void awaitMerge(final long storeId) {
        countLock.await(storeId);
    }

    private static class CountLock {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CountLock.class);

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private long currentCount = -1;

        public void setCount(final long count) {
            try {
                lock.lockInterruptibly();
                try {
                    currentCount = Math.max(currentCount, count);
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e::getMessage, e);
                Thread.currentThread().interrupt();
            }
        }

        public void await(final long count) {
            try {
                lock.lockInterruptibly();
                try {
                    while (currentCount < count) {
                        condition.await();
                    }
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e::getMessage, e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
