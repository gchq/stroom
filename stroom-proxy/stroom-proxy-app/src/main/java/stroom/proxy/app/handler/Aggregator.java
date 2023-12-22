package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.receive.common.ProgressHandler;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Aggregator implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Aggregator.class);

    private final PreAggregateDirQueue preAggregateDirQueue;
    private final CleanupDirQueue deleteDirQueue;
    private final ForwardDirQueue forwardDirQueue;
    private final NumberedDirProvider tempAggregatingDirProvider;
    private final AggregatorConfig aggregatorConfig;
    private CompletableFuture<Void> completableFuture;
    private volatile boolean running;


    @Inject
    public Aggregator(final PreAggregateDirQueue preAggregateDirQueue,
                      final CleanupDirQueue deleteDirQueue,
                      final ForwardDirQueue forwardDirQueue,
                      final Provider<ProxyConfig> proxyConfigProvider,
                      final TempDirProvider tempDirProvider) {
        this.preAggregateDirQueue = preAggregateDirQueue;
        this.deleteDirQueue = deleteDirQueue;
        this.forwardDirQueue = forwardDirQueue;
        this.aggregatorConfig = proxyConfigProvider.get().getAggregatorConfig();

        // Make aggregating dir.
        final Path aggregatingDir = tempDirProvider.get().resolve("10_aggregating");
        ensureDirExists(aggregatingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(aggregatingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(aggregatingDir));
        }

        tempAggregatingDirProvider = new NumberedDirProvider(aggregatingDir);
    }

    private void ensureDirExists(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create " + FileUtil.getCanonicalPath(dir), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void start() throws Exception {
        if (!running && aggregatorConfig.isEnabled()) {
            running = true;

            // TODO : We could introduce more threads here.

            completableFuture = CompletableFuture.runAsync(() -> {
                while (running) {
                    final SequentialDir sequentialDir = preAggregateDirQueue.next();
                    addDir(sequentialDir.getDir());
                    // Delete empty dirs.
                    sequentialDir.deleteEmptyParentDirs();
                }
            });
        }
    }

    private void addDir(final Path dir) {
        try {
            // First count all files.
            final long sourceDirCount;
            try (final Stream<Path> stream = Files.list(dir)) {
                sourceDirCount = stream.count();
            }

            LOGGER.debug(() -> "Aggregating " + sourceDirCount + " items");

            if (sourceDirCount == 0) {
                throw new RuntimeException("Unexpected dir count");

            } else if (sourceDirCount == 1) {
                // If we only have one source dir then no merging is required, just forward.
                forwardDirQueue.add(dir);

            } else {
                // Merge the files into an aggregate.
                final Path tempDir = tempAggregatingDirProvider.get();
                final FileGroup outputFileGroup = new FileGroup(tempDir);
                final AtomicLong count = new AtomicLong();
                final AtomicBoolean doneMeta = new AtomicBoolean();
                // Get a buffer to help us transfer data.
                final byte[] buffer = LocalByteBuffer.get();

                try (final ZipOutputStream zipOutputStream =
                        new ZipOutputStream(
                                new BufferedOutputStream(Files.newOutputStream(outputFileGroup.getZip())))) {
                    try (final Stream<Path> stream = Files.list(dir)) {
                        stream.forEach(fileGroupDir -> {
                            final FileGroup fileGroup = new FileGroup(fileGroupDir);

                            // Output meta if this is the first.
                            if (!doneMeta.get()) {
                                try {
                                    Files.copy(fileGroup.getMeta(), outputFileGroup.getMeta());
                                    doneMeta.set(true);
                                } catch (final IOException e) {
                                    LOGGER.error(e::getMessage, e);
                                    throw new UncheckedIOException(e);
                                }
                            }

                            try (final ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                                    Files.newInputStream(fileGroup.getZip())))) {

                                String lastBaseName = null;
                                String outputBaseName = null;
                                ZipEntry zipEntry = zipInputStream.getNextEntry();
                                while (zipEntry != null) {
                                    final String name = zipEntry.getName();
                                    final FileName fileName = FileName.parse(name);
                                    final String baseName = fileName.getBaseName();
                                    if (lastBaseName == null || !lastBaseName.equals(baseName)) {
                                        outputBaseName = NumericFileNameUtil.create(count.incrementAndGet());
                                        lastBaseName = baseName;
                                    }

                                    zipOutputStream.putNextEntry(
                                            new ZipEntry(outputBaseName + "." + fileName.getExtension()));
                                    transfer(zipInputStream, zipOutputStream, buffer);

                                    zipEntry = zipInputStream.getNextEntry();
                                }

                            } catch (final IOException e) {
                                LOGGER.error(e::getMessage, e);
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                }

                // We have finished the merge so transfer the new item to be forwarded.
                forwardDirQueue.add(tempDir);

                // Delete the source.
                deleteDirQueue.add(dir);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            running = false;
            completableFuture.join();
        }
    }

    private long transfer(final InputStream in, final OutputStream out, final byte[] buffer) {
        return StreamUtil
                .streamToStream(in,
                        out,
                        buffer,
                        new ProgressHandler("Receiving data"));
    }
}
