package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.io.AbstractLmdbWriter;
import stroom.planb.impl.io.RangedStateWriter;
import stroom.planb.impl.io.SessionWriter;
import stroom.planb.impl.io.StatePaths;
import stroom.planb.impl.io.StateWriter;
import stroom.planb.impl.io.TemporalRangedStateWriter;
import stroom.planb.impl.io.TemporalStateWriter;
import stroom.planb.shared.PlanBDoc;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class ShardManager {

    private final ByteBufferFactory byteBufferFactory;
    private final Path shardDir;
    private final PlanBDocCache planBDocCache;


    private final ReentrantLock reentrantLock = new ReentrantLock();
    private volatile String currentMapName;
    private volatile AbstractLmdbWriter<?, ?> currentWriter;

    @Inject
    public ShardManager(final ByteBufferFactory byteBufferFactory,
                        final PlanBDocCache planBDocCache,
                        final StatePaths statePaths) {
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        shardDir = statePaths.getShardDir();
    }

    public void merge(final Path sourceDir) throws IOException {
        final String mapName = sourceDir.getFileName().toString();
        final PlanBDoc doc = planBDocCache.get(mapName);
        if (doc != null) {
            // Get shard dir.
            final Path target = shardDir.resolve(mapName);

            // If we don't already have the shard dir then just move the source to the target.
            if (!Files.isDirectory(target)) {
                lock(() -> {
                    try {
                        Files.createDirectories(shardDir);
                        Files.move(sourceDir, target);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

            } else {
                // If we do already have a target then merge the source to the target.
                lock(() -> {
                    currentMapName = mapName;
                    currentWriter = getWriter(doc, target);
                });
                try {
                    currentWriter.merge(sourceDir);
                } finally {
                    lock(() -> {
                        currentMapName = null;
                        currentWriter.close();
                    });
                }
            }
        }
    }

    public void zip(final String mapName, final OutputStream outputStream) throws IOException {
        final PlanBDoc doc = planBDocCache.get(mapName);
        if (doc != null) {

            // Get shard dir.
            final Path shard = shardDir.resolve(mapName);
            if (!Files.exists(shard)) {
                throw new RuntimeException("Shard not found");
            }
            final Path lmdbDataFile = shard.resolve("data.mdb");
            if (!Files.exists(lmdbDataFile)) {
                throw new RuntimeException("LMDB data file not found");
            }

            lock(() -> {
                if (currentMapName.equals(mapName)) {
                    if (currentWriter != null) {
                        currentWriter.lock(() -> zip(shard, outputStream));
                    } else {
                        zip(shard, outputStream);
                    }
                } else {
                    zip(shard, outputStream);
                }
            });
        }
    }

    private void lock(final Runnable runnable) {
        reentrantLock.lock();
        try {
            runnable.run();
        } finally {
            reentrantLock.unlock();
        }
    }

    private void zip(final Path shard, final OutputStream outputStream) {
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(outputStream))) {
            ZipUtil.zip(shard, zipOutputStream);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private AbstractLmdbWriter<?, ?> getWriter(final PlanBDoc doc, final Path targetPath) {
        switch (doc.getStateType()) {
            case STATE -> {
                return new StateWriter(targetPath, byteBufferFactory);
            }
            case TEMPORAL_STATE -> {
                return new TemporalStateWriter(targetPath, byteBufferFactory);
            }
            case RANGED_STATE -> {
                return new RangedStateWriter(targetPath, byteBufferFactory);
            }
            case TEMPORAL_RANGED_STATE -> {
                return new TemporalRangedStateWriter(targetPath, byteBufferFactory);
            }
            case SESSION -> {
                return new SessionWriter(targetPath, byteBufferFactory);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
