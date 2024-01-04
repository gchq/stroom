package stroom.proxy.app.handler;

import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Aggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Aggregator.class);

    private final CleanupDirQueue deleteDirQueue;
    private final NumberedDirProvider tempAggregatesDirProvider;

    private Consumer<Path> destination;


    @Inject
    public Aggregator(final CleanupDirQueue deleteDirQueue,
                      final TempDirProvider tempDirProvider) {
        this.deleteDirQueue = deleteDirQueue;

        // Make temp aggregates dir.
        final Path aggregatesDir = tempDirProvider.get().resolve(DirNames.AGGREGATES);
        DirUtil.ensureDirExists(aggregatesDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(aggregatesDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(aggregatesDir));
        }

        tempAggregatesDirProvider = new NumberedDirProvider(aggregatesDir);
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
                try (final Stream<Path> stream = Files.list(dir)) {
                    stream.forEach(fileGroupDir -> destination.accept(fileGroupDir));
                }

            } else {
                // Merge the files into an aggregate.
                final Path tempDir = tempAggregatesDirProvider.get();
                final FileGroup outputFileGroup = new FileGroup(tempDir);
                final AtomicLong count = new AtomicLong();
                final AtomicBoolean doneMeta = new AtomicBoolean();
                // Get a buffer to help us transfer data.
                final byte[] buffer = LocalByteBuffer.get();

                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(outputFileGroup.getZip(), buffer)) {
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

                            try (final ZipArchiveInputStream zipInputStream =
                                    new ZipArchiveInputStream(
                                            new BufferedInputStream(Files.newInputStream(fileGroup.getZip())))) {
                                String lastBaseName = null;
                                String outputBaseName = null;
                                ZipArchiveEntry zipEntry = zipInputStream.getNextEntry();
                                while (zipEntry != null) {
                                    final String name = zipEntry.getName();
                                    final FileName fileName = FileName.parse(name);
                                    final String baseName = fileName.getBaseName();
                                    if (lastBaseName == null || !lastBaseName.equals(baseName)) {
                                        outputBaseName = NumericFileNameUtil.create(count.incrementAndGet());
                                        lastBaseName = baseName;
                                    }

                                    zipWriter.writeStream(
                                            outputBaseName + "." + fileName.getExtension(),
                                            zipInputStream);

                                    zipEntry = zipInputStream.getNextEntry();
                                }

//                            try (final BufferedReader entryReader = Files.newBufferedReader(fileGroup.getEntries())) {
//                                try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fileGroup.getZip()))) {
//                                    String entryLine = entryReader.readLine();
//                                    while (entryLine != null) {
//                                        // Add entry.
//                                        final ZipEntryGroup zipEntryGroup = ZipEntryGroupUtil.readLine(entryLine);
//                                        final String baseNameOut = NumericFileNameUtil.create(count.incrementAndGet());
//
//                                        TransferUtil.transferZipEntryGroup(
//                                                zipFile,
//                                                zipWriter,
//                                                zipEntryGroup,
//                                                baseNameOut);
//
////                                        final ZipEntryGroup outZipEntryGroup =
////                                        // Write the entry.
////                                        ZipEntryGroupUtil.writeLine(entryWriter, outZipEntryGroup);
//
//
//                                        entryLine = entryReader.readLine();
//                                    }
//                                }
                            } catch (final IOException e) {
                                LOGGER.error(e::getMessage, e);
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                }

                // We have finished the merge so transfer the new item to be forwarded.
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
