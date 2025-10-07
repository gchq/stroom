package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.DirScannerConfig;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ZipDirScanner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipDirScanner.class);
    private static final String ZIP_EXTENSION = ".zip";

    private final Provider<DirScannerConfig> dirScannerConfigProvider;
    private final PathCreator pathCreator;
    private final ZipReceiver zipReceiver;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final NestedNumberedDirProvider failureDirProvider;

    @Inject
    public ZipDirScanner(final Provider<DirScannerConfig> dirScannerConfigProvider,
                         final PathCreator pathCreator,
                         final ZipReceiver zipReceiver,
                         final ReceiptIdGenerator receiptIdGenerator) {
        this.dirScannerConfigProvider = dirScannerConfigProvider;
        this.pathCreator = pathCreator;
        this.zipReceiver = zipReceiver;
        this.receiptIdGenerator = receiptIdGenerator;
        final Path failureDir = pathCreator.toAppPath(dirScannerConfigProvider.get().getFailureDir());
        FileUtil.ensureDirExists(failureDir);
        this.failureDirProvider = NestedNumberedDirProvider.create(failureDir);
    }

    public void scan() {
        try {
            final DirScannerConfig dirScannerConfig = dirScannerConfigProvider.get();
            if (dirScannerConfig.isEnabled()) {
                final List<Path> dirs = getPathsToScan(dirScannerConfig);
                if (NullSafe.hasItems(dirs)) {
                    for (final Path dir : dirs) {
                        scanDir(dir);
                    }
                }
            } else {
                LOGGER.debug("scan() - disabled");
            }
        } catch (final Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("scan() - Error during scan - {}",
                        LogUtil.exceptionMessage(e), e);
            } else {
                LOGGER.error("scan() - Error during scan - {} (enable DEBUG for stack trace)",
                        LogUtil.exceptionMessage(e));
            }
            // We need to swallow the error so the scheduled executor can try again next time
        }
    }

    private void processZipFile(final Path zipFile) {
        LOGGER.debug("processZipFile() - {}", zipFile);
        // Need to swallow all exceptions, so we don't halt the dir walking
        try {
            // Create a receiptId for the zip and set the receipt time
            final AttributeMap attributeMap = new AttributeMap();
            final UniqueId receiptId = receiptIdGenerator.generateId();
            AttributeMapUtil.addReceiptInfo(attributeMap, receiptId);

            zipReceiver.receive(zipFile, attributeMap);
            // receive will have cloned our zip, so as there were no problems, we can now delete it
            LOGGER.debug("processZipFile() - Deleting {}", zipFile);
            Files.deleteIfExists(zipFile);
        } catch (final Exception e) {
            LOGGER.error("Error processing zipFile {} - {}", zipFile, LogUtil.exceptionMessage(e), e);
            if (Files.exists(zipFile)) {
                final Path dir = failureDirProvider.createNumberedPath();
                final Path dest = dir.resolve(zipFile.getFileName());
                try {
                    Files.move(zipFile, dest, StandardCopyOption.ATOMIC_MOVE);
                } catch (final IOException ex) {
                    LOGGER.error("Error moving failed zip from {} to {}", zipFile, dest, e);
                }
            } else {
                LOGGER.debug("processZipFile() - zipFile {} doesn't exist", zipFile);
            }
        }
    }

    private void scanDir(final Path rootDir) {
        LOGGER.debug("scanDir() - {}", rootDir);
        final DurationTimer timer = DurationTimer.start();

        try {
            Files.walkFileTree(rootDir, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                        throws IOException {
                    LOGGER.debug("scanDir() - preVisitDirectory {}", dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    LOGGER.debug("scanDir() - visitFile {}", file);
                    if (isZipFile(file)) {
                        processZipFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path path, final IOException exc) throws IOException {
                    LOGGER.debug(() -> LogUtil.message(
                            "scanDir() - unable to read file/dir {}: {}",
                            path, LogUtil.exceptionMessage(exc), exc));
                    if (isZipFile(path)) {
                        LOGGER.error("scanDir() - unable to read zip file {}: {}",
                                path, LogUtil.exceptionMessage(exc));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    LOGGER.debug("scanDir() - postVisitDirectory {}", dir);
                    deleteDirectoryIfEmpty(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            LOGGER.error("scanDir() - unable to read zip file {}: {}",
                    rootDir, LogUtil.exceptionMessage(e));
            throw new RuntimeException(e);
        }

        LOGGER.debug("scanDir() - Scanned {} in {}", rootDir, timer);
    }

    private void deleteDirectoryIfEmpty(final Path dir) {
        try {
            if (Files.isDirectory(dir) && Files.isWritable(dir)) {
                final boolean isEmpty;
                try (final Stream<Path> entries = Files.list(dir)) {
                    isEmpty = entries.findAny().isEmpty();
                }
                if (isEmpty) {
                    // May fail if something has been dropped in since we checked, but that is OK
                    // as we will check it again on next run.
                    Files.delete(dir);
                }
            }
        } catch (final IOException e) {
            // Just swallow it
            LOGGER.debug(() -> LogUtil.message("Unable to delete directory {} - ",
                    dir, LogUtil.exceptionMessage(e), e));
        }
    }

    private boolean isZipFile(final Path path) {
        Objects.requireNonNull(path);
        return Files.isRegularFile(path)
               && path.getFileName().toString().toLowerCase().endsWith(ZIP_EXTENSION);
    }

    private List<Path> getPathsToScan(final DirScannerConfig dirScannerConfig) {
        return NullSafe.stream(dirScannerConfig.getDirs())
                .map(pathCreator::toAppPath)
                .filter(path -> {
                    final boolean isDir = Files.isDirectory(path);
                    if (isDir) {
                        return true;
                    } else {
                        LOGGER.warn("getPathsToScan() - path '{}' does not exist or is not a directory. " +
                                    "It will be ignored.", path);
                        return false;
                    }
                })
                .toList();
    }
}
