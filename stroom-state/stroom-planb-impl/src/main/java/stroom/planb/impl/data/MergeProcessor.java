package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.io.RangedStateWriter;
import stroom.planb.impl.io.SessionWriter;
import stroom.planb.impl.io.StatePaths;
import stroom.planb.impl.io.StateWriter;
import stroom.planb.impl.io.TemporalRangedStateWriter;
import stroom.planb.impl.io.TemporalStateWriter;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
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

    public static final String TASK_NAME = "PlanB Merge Processor";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MergeProcessor.class);

    private final SequentialFileStore fileStore;
    private final ByteBufferFactory byteBufferFactory;
    private final Path mergingDir;
    private final Path shardDir;
    private final PlanBDocCache planBDocCache;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public MergeProcessor(final SequentialFileStore fileStore,
                          final ByteBufferFactory byteBufferFactory,
                          final PlanBDocCache planBDocCache,
                          final StatePaths statePaths,
                          final SecurityContext securityContext,
                          final TaskContextFactory taskContextFactory) {
        this.fileStore = fileStore;
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;

        mergingDir = statePaths.getMergingDir();
        if (ensureDirExists(mergingDir)) {
            if (!FileUtil.deleteContents(mergingDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(mergingDir));
            }
        }
        shardDir = statePaths.getShardDir();
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
            taskContextFactory.context("PlanB state merge", taskContext -> {
                try {
                    final long minStoreId = fileStore.getMinStoreId();
                    final long maxStoreId = fileStore.getMaxStoreId();
                    LOGGER.info(() -> LogUtil.message("Min store id = {}, max store id = {}", minStoreId, maxStoreId));

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
            }).run();
        });
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
                        final String mapName = source.getFileName().toString();
                        final PlanBDoc doc = planBDocCache.get(mapName);
                        if (doc != null) {
                            // Get shard dir.
                            final Path target = shardDir.resolve(mapName);
                            if (!Files.isDirectory(target)) {
                                Files.createDirectories(shardDir);
                                Files.move(source, target);
                            } else {
                                // merge.
                                merge(doc, source, target);

                                // Delete source.
                                FileUtil.deleteDir(source);
                            }
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            // Delete the original zip file.
            sequentialFile.delete();
        }
    }

    private void merge(final PlanBDoc doc, final Path sourcePath, final Path targetPath) {
        switch (doc.getStateType()) {
            case STATE -> {
                try (final StateWriter writer =
                        new StateWriter(targetPath, byteBufferFactory)) {
                    writer.merge(sourcePath);
                }
            }
            case TEMPORAL_STATE -> {
                try (final TemporalStateWriter writer =
                        new TemporalStateWriter(targetPath, byteBufferFactory)) {
                    writer.merge(sourcePath);
                }
            }
            case RANGED_STATE -> {
                try (final RangedStateWriter writer =
                        new RangedStateWriter(targetPath, byteBufferFactory)) {
                    writer.merge(sourcePath);
                }
            }
            case TEMPORAL_RANGED_STATE -> {
                try (final TemporalRangedStateWriter writer =
                        new TemporalRangedStateWriter(targetPath, byteBufferFactory)) {
                    writer.merge(sourcePath);
                }
            }
            case SESSION -> {
                try (final SessionWriter writer =
                        new SessionWriter(targetPath, byteBufferFactory)) {
                    writer.merge(sourcePath);
                }
            }
        }
    }
}
