package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBConfig;
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
import stroom.util.io.PathCreator;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
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

    private final SequentialFileStore fileStore;
    private final ByteBufferFactory byteBufferFactory;
    private final Path mergingDir;
    private final Path shardDir;
    private final PlanBDocCache planBDocCache;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public MergeProcessor(final SequentialFileStore fileStore,
                          final Provider<PlanBConfig> configProvider,
                          final PathCreator pathCreator,
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
            taskContextFactory.context("State Condenser", taskContext -> {
                try {
                    final long storeId = fileStore.awaitNew(0);
                    final SequentialFile sequentialFile = fileStore.getStoreFileSet(storeId);
                    final Path zipFile = sequentialFile.getZip();
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

                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });
    }

    private void merge(final PlanBDoc doc, final Path sourcePath, final Path targetPath) {
        switch (doc.getStateType()) {
            case STATE -> {
                try (final StateWriter writer =
                        new StateWriter(targetPath, byteBufferFactory, false)) {
                    writer.merge(sourcePath);
                }
            }
            case TEMPORAL_STATE -> {
                try (final TemporalStateWriter writer =
                        new TemporalStateWriter(targetPath, byteBufferFactory, false)) {
                    writer.merge(sourcePath);
                }
            }
            case RANGED_STATE -> {
                try (final RangedStateWriter writer =
                        new RangedStateWriter(targetPath, byteBufferFactory, false)) {
                    writer.merge(sourcePath);
                }
            }
            case TEMPORAL_RANGED_STATE -> {
                try (final TemporalRangedStateWriter writer =
                        new TemporalRangedStateWriter(targetPath, byteBufferFactory, false)) {
                    writer.merge(sourcePath);
                }
            }
            case SESSION -> {
                try (final SessionWriter writer =
                        new SessionWriter(targetPath, byteBufferFactory, false)) {
                    writer.merge(sourcePath);
                }
            }
        }
    }
}
