package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Singleton
public class Aggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Aggregator.class);

    private final CleanupDirQueue deleteDirQueue;
    private final NumberedDirProvider tempAggregatesDirProvider;

    private Consumer<Path> destination;

    @Inject
    public Aggregator(final CleanupDirQueue deleteDirQueue,
                      final DataDirProvider dataDirProvider) {
        this.deleteDirQueue = deleteDirQueue;

        // Make temp aggregates dir.
        final Path aggregatesDir = dataDirProvider.get().resolve(DirNames.AGGREGATES);
        DirUtil.ensureDirExists(aggregatesDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(aggregatesDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(aggregatesDir));
        }

        tempAggregatesDirProvider = new NumberedDirProvider(aggregatesDir);

        LOGGER.info("Initialised Aggregator with aggregatesDir: {}", aggregatesDir);
    }

    public void addDir(final Path dir) {
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
                FileUtil.forEachChild(dir, fileGroupDir -> {
                    LOGGER.debug("Passing {} to destination {} with no merging", fileGroupDir, destination);
                    destination.accept(fileGroupDir);
                });

            } else {
                // Merge the files into an aggregate.
                final Path tempDir = tempAggregatesDirProvider.get();
                final FileGroup outputFileGroup = new FileGroup(tempDir);
                final AtomicLong count = new AtomicLong();
                final AtomicBoolean doneMeta = new AtomicBoolean();
                // Get a buffer to help us transfer data.
                final byte[] buffer = LocalByteBuffer.get();

                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(outputFileGroup.getZip(), buffer)) {
                    FileUtil.forEachChild(dir, fileGroupDir -> {
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

                        try (final ZipFile zipFile = ZipUtil.createZipFile(fileGroup.getZip())) {
                            final Iterator<ZipArchiveEntry> entries = zipFile.getEntries().asIterator();

                            String lastBaseName = null;
                            String outputBaseName = null;
                            while (entries.hasNext()) {
                                final ZipArchiveEntry zipEntry = entries.next();
                                final String name = zipEntry.getName();
                                final FileName fileName = FileName.parse(name);
                                final String baseName = fileName.getBaseName();
                                if (lastBaseName == null || !lastBaseName.equals(baseName)) {
                                    outputBaseName = NumericFileNameUtil.create(count.incrementAndGet());
                                    lastBaseName = baseName;
                                }

                                // No need to decompress+recompress the entry as only the name is changing,
                                // just write the raw compressed data into the new zip. Much faster.
                                zipWriter.writeRawStream(
                                        zipEntry,
                                        outputBaseName + "." + fileName.getExtension(),
                                        zipFile.getRawInputStream(zipEntry));
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e::getMessage, e);
                            throw new UncheckedIOException(e);
                        }
                    });
                }

                // We have finished the merge so transfer the new item to be forwarded.
                LOGGER.debug("Passing {} to destination {}", tempDir, destination);
                destination.accept(tempDir);
            }

            // Delete the source dir.
            deleteDirQueue.add(dir);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }
}
