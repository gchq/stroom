package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

@Singleton
public class MergeProcessor {

    public static final String TASK_NAME = "Plan B Merge Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final SequentialFileStore fileStore;
    private final Path mergingDir;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;

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
        if (ensureDirExists(mergingDir)) {
            if (!FileUtil.deleteContents(mergingDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(mergingDir));
            }
        }
    }

    private boolean ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return true;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return false;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            final TaskContext taskContext = taskContextFactory.current();
            try {
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

                while (!taskContext.isTerminated() && !Thread.currentThread().isInterrupted()) {
                    // Wait until new data is available.
                    final long currentStoreId = storeId;
                    taskContext.info(() -> "Waiting for data...");
                    final SequentialFile sequentialFile = fileStore.awaitNew(currentStoreId);
                    taskContext.info(() -> "Merging data: " + currentStoreId);
                    merge(sequentialFile);

                    // Increment store id.
                    storeId++;
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void mergeCurrent() throws IOException {
        final long start = fileStore.getMinStoreId();
        final long end = fileStore.getMaxStoreId();
        for (long storeId = start; storeId <= end; storeId++) {
            // Wait until new data is available.
            final SequentialFile sequentialFile = fileStore.awaitNew(storeId);
            merge(sequentialFile);
        }
    }

    public void merge(final long storeId) throws IOException {
        // Wait until new data is available.
        final SequentialFile sequentialFile = fileStore.awaitNew(storeId);
        merge(sequentialFile);
    }

    private void merge(final SequentialFile sequentialFile) throws IOException {
        final Path zipFile = sequentialFile.getZip();
        if (Files.isRegularFile(zipFile)) {
            final Path dir = mergingDir.resolve(UUID.randomUUID().toString());
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

            // Delete the original zip file.
            sequentialFile.delete();
        }
    }
}
