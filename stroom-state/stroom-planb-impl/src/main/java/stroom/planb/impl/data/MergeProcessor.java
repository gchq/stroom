package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Singleton
public class MergeProcessor {

    public static final String MERGE_TASK_NAME = "Plan B Merge Processor";
    public static final String MAINTAIN_TASK_NAME = "Plan B Maintenance Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final Map<String, DirQueue> mergeQueues = new ConcurrentHashMap<>();
    private final StagingFileStore fileStore;
    private final Path mergingDir;
    private final Path unzipDirRoot;
    private final AtomicLong unzipSequenceId = new AtomicLong();
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private volatile boolean merging;

    @Inject
    public MergeProcessor(final StagingFileStore fileStore,
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
        unzipDirRoot = mergingDir.resolve("unzip");
    }

    public void merge() {
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
                    });
                }
            }
        }
    }

    private void unzipPartFiles() {
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
                taskContextFactory.context(MERGE_TASK_NAME, taskContext -> {
                    taskContext.info(() -> "Decompressing received data: " + currentStoreId);
                    unzipPartFile(taskContext, sequentialFile);
                }).run();
                // Increment store id.
                storeId++;
            }
        });
    }

    public void maintainShards() {
        securityContext.asProcessingUser(shardManager::condenseAll);
    }

    public void mergeCurrent() {
        final long start = fileStore.getMinStoreId();
        final long end = fileStore.getMaxStoreId();
        for (long storeId = start; storeId <= end; storeId++) {
            merge(storeId);
        }
    }

    public void merge(final long storeId) {
        // Wait until new data is available.
        final SequentialFile sequentialFile = fileStore.awaitNext(storeId);
        taskContextFactory.context(MERGE_TASK_NAME, parentContext -> {
            try {
                final Path zipFile = sequentialFile.getZip();
                if (Files.isRegularFile(zipFile)) {
                    final String unzipDirName = StringIdUtil.idToString(unzipSequenceId.incrementAndGet());
                    final Path unzipDir = unzipDirRoot.resolve(unzipDirName);
                    ZipUtil.unzip(zipFile, unzipDir);

                    // We ought to have one or more stores to merge in this part zip file.
                    try (final Stream<Path> stream = Files.list(unzipDir)) {
                        stream.forEach(source -> {
                            final String docUuid = source.getFileName().toString();
                            mergeDir(parentContext, source, docUuid);
                        });
                    }

                    // Delete unzip dir.
                    FileUtil.deleteDir(unzipDir);

                    // Delete the original zip file.
                    fileStore.delete(sequentialFile);
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }).run();
    }

    private void unzipPartFile(final TaskContext parentContext, final SequentialFile sequentialFile) {
        try {
            final Path zipFile = sequentialFile.getZip();
            if (Files.isRegularFile(zipFile)) {
                final String unzipDirName = StringIdUtil.idToString(unzipSequenceId.incrementAndGet());
                final Path unzipDir = unzipDirRoot.resolve(unzipDirName);
                ZipUtil.unzip(zipFile, unzipDir);

                // We ought to have one or more stores to merge in this part zip file.
                try (final Stream<Path> stream = Files.list(unzipDir)) {
                    stream.forEach(source -> {
                        final String docUuid = source.getFileName().toString();
                        final DirQueue queue = mergeQueues.computeIfAbsent(docUuid, k -> {
                            try {
                                final Path uuidDir = mergingDir.resolve(docUuid);
                                Files.createDirectories(uuidDir);
                                final DirQueue dirQueue = new DirQueue(uuidDir, docUuid);
                                // Start processing this queue.
                                CompletableFuture.runAsync(() ->
                                        mergeStore(parentContext, dirQueue, docUuid));
                                return dirQueue;
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                        queue.add(source);
                    });
                }

                // Delete unzip dir.
                FileUtil.deleteDir(unzipDir);

                // Delete the original zip file.
                fileStore.delete(sequentialFile);
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void mergeStore(final TaskContext parentContext,
                            final DirQueue dirQueue,
                            final String uuid) {
        securityContext.asProcessingUser(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // Wait until new data is available.
                try (final Dir dir = dirQueue.next()) {
                    mergeDir(parentContext, dir.getPath(), uuid);
                }
            }
        });
    }

    private void mergeDir(final TaskContext parentContext,
                          final Path path,
                          final String uuid) {
        taskContextFactory.childContext(parentContext, uuid, taskContext -> {
            getShard(uuid).ifPresent(shard -> {
                taskContext.info(() -> "Merging data into '" +
                                       NullSafe.get(shard, Shard::getDoc, PlanBDoc::getName) +
                                       "'");
                shard.merge(path);
            });
            FileUtil.deleteDir(path);
        }).run();
    }

    private Optional<Shard> getShard(final String docUuid) {
        try {
            // The doc might have been deleted so catch this error.
            return Optional.of(shardManager.getShardForDocUuid(docUuid));
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return Optional.empty();
    }
}
