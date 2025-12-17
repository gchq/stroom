/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
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
import jakarta.inject.Singleton;
import org.apache.commons.compress.utils.FileNameUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Scans one or more configured directories in sequence looking for ZIP files to ingest as if they had
 * been forwarded from another proxy.
 * Will recurse into subdirectories. Once the scan is complete any empty directories inside the configured
 * root directories will be deleted.
 */
@Singleton // So we can synchronise
public class ZipDirScanner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipDirScanner.class);
    private static final Set<String> SIDECAR_EXTENSIONS = Set.of(
            FileGroup.META_EXTENSION,
            FileGroup.ENTRIES_FILE);
    private static final Set<String> SIDECAR_FILENAMES = Set.of(
            "error.log");

    private final Provider<DirScannerConfig> dirScannerConfigProvider;
    private final PathCreator pathCreator;
    private final ZipReceiver zipReceiver;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final NestedNumberedDirProvider failureDirProvider;
    private final Path failureDir;

    @Inject
    public ZipDirScanner(final Provider<DirScannerConfig> dirScannerConfigProvider,
                         final PathCreator pathCreator,
                         final ZipReceiver zipReceiver,
                         final ReceiptIdGenerator receiptIdGenerator) {
        this.dirScannerConfigProvider = dirScannerConfigProvider;
        this.pathCreator = pathCreator;
        this.zipReceiver = zipReceiver;
        this.receiptIdGenerator = receiptIdGenerator;

        this.failureDir = pathCreator.toAppPath(dirScannerConfigProvider.get().getFailureDir());
        FileUtil.ensureDirExists(failureDir);
        this.failureDirProvider = NestedNumberedDirProvider.create(failureDir);
    }

    public synchronized void scan() {
        try {
            final DirScannerConfig dirScannerConfig = dirScannerConfigProvider.get();
            if (dirScannerConfig.isEnabled()) {
                final List<Path> dirs = getPathsToScan(dirScannerConfig);
                if (NullSafe.hasItems(dirs)) {
                    final ScanResult scanResult = new ScanResult();
                    final DurationTimer timer = DurationTimer.start();
                    for (final Path dir : dirs) {
                        // This will log and swallow any exceptions to ensure we can keep processing
                        scanDir(dir, scanResult);
                    }
                    if (!scanResult.isEmpty()) {
                        LOGGER.info("Completed scan for ZIP files to ingest, success: {}, failed: {}, " +
                                    "unknown files: {}, duration: {}",
                                scanResult.successCount, scanResult.failCount, scanResult.unknownCount, timer);
                    } else {
                        LOGGER.debug("Completed scan for ZIP files to ingest, success: {}, failed: {}, " +
                                     "unknown files: {}, duration: {}",
                                scanResult.successCount, scanResult.failCount, scanResult.unknownCount, timer);
                    }
                } else {
                    LOGGER.debug("scan() - No dirs to scan");
                }
            } else {
                LOGGER.debug("scan() - disabled");
            }
        } catch (final Exception e) {
            logError(e);
            // We need to swallow the error so the scheduled executor can try again next time
        }
    }

    private void logError(final Exception e) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("scan() - Error during scan - {}",
                    LogUtil.exceptionMessage(e), e);
        } else {
            LOGGER.error("scan() - Error during scan - {} (enable DEBUG for stack trace)",
                    LogUtil.exceptionMessage(e));
        }
    }

    private void processZipFile(final Path zipFile, final ScanResult scanResult) {
        LOGGER.debug("processZipFile() - {}", zipFile);
        final ZipGroup zipGroup = createZipGroup(zipFile);
        // Need to swallow all exceptions, so we don't halt the dir walking
        try {
            final AttributeMap attributeMap = createAttributeMap(zipGroup);

            zipReceiver.receive(zipFile, attributeMap);
            // receive will have cloned our zip, so as there were no problems, we can now delete it
            // and the other files in the group
            deleteZipGroup(zipGroup);
            scanResult.incrementSuccessCount();
            LOGGER.info("Ingested ZIP file {}", zipFile);
        } catch (final Exception e) {
            scanResult.incrementFailCount();
            final Path destDir = failureDirProvider.createNumberedPath();
            if (Files.exists(zipFile)) {
                LOGGER.error("Error processing zipFile {}, moving it (and any associated sidecar files) into {} - {}",
                        zipFile, destDir, LogUtil.exceptionMessage(e), e);
                // Move the zip and its associated sidecar files to a failure dir
                zipGroup.streamPaths()
                        .forEach(sourceFile -> {
                            final Path destFile = destDir.resolve(sourceFile.getFileName());
                            try {
                                Files.move(sourceFile, destFile);
                            } catch (final IOException ex) {
                                LOGGER.error("Error moving failed file from {} to {}", sourceFile, destFile, e);
                            }
                        });
            } else {
                LOGGER.error("Error processing zipFile {} - {}", zipFile, LogUtil.exceptionMessage(e), e);
                LOGGER.debug("processZipFile() - zipFile {} doesn't exist", zipFile);
            }
        }
    }

    private void deleteZipGroup(final ZipGroup zipGroup) {
        zipGroup.streamPaths()
                .forEach(path -> {
                    try {
                        LOGGER.debug("deleteZipGroup() - Deleting file {}", path);
                        Files.delete(path);
                    } catch (final Exception e) {
                        LOGGER.error("Error deleting file {}. This file needs to be manually deleted " +
                                     "or it risks being re-ingested. - {}", path, LogUtil.exceptionMessage(e), e);
                    }
                });
    }

    private ZipGroup createZipGroup(final Path zipFile) {
        // We may be dealing with just a zip file or a set of files moved in from the forward failure
        // dir, e.g.
        // ./03_failure/20251014/BAD_FEED/0/001/proxy.zip
        // ./03_failure/20251014/BAD_FEED/0/001/proxy.meta
        // ./03_failure/20251014/BAD_FEED/0/001/proxy.entries
        // ./03_failure/20251014/BAD_FEED/0/001/error.log
        // We assume that apart from the error.log file (which has a specific name), any sidecar files
        // will the same base name as our zip

        final Path parentDir = zipFile.getParent();
        final String baseName = FileNameUtils.getBaseName(zipFile);
        Path metaFile = parentDir.resolve(baseName + "." + StroomZipFileType.META.getExtension());
        if (!Files.isRegularFile(metaFile)) {
            metaFile = null;
        }
        // This one has a specific name
        Path errorFile = parentDir.resolve(RetryingForwardDestination.ERROR_LOG_FILENAME);
        if (!Files.isRegularFile(errorFile)) {
            errorFile = null;
        }

        Path entriesFile = parentDir.resolve(baseName + "." + FileGroup.ENTRIES_EXTENSION);
        if (!Files.isRegularFile(entriesFile)) {
            entriesFile = null;
        }
        final ZipGroup zipGroup = new ZipGroup(zipFile, metaFile, errorFile, entriesFile);
        LOGGER.debug("createZipGroup() - zipGroup: {}", zipGroup);
        return zipGroup;
    }

    private AttributeMap createAttributeMap(final ZipGroup zipGroup) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        if (zipGroup.hasMetaFile() && Files.isRegularFile(zipGroup.metaFile)) {
            AttributeMapUtil.read(zipGroup.metaFile, attributeMap);
            LOGGER.debug("createAttributeMap() - Read attributes from {}, attributeMap: {}",
                    zipGroup.metaFile, attributeMap);
        } else {
            LOGGER.debug("createAttributeMap() - File {} not found, creating minimal attributeMap",
                    zipGroup.metaFile);
            // We may already have a receiptId, but this will just set a new one and append
            // to ReceiptIdPath
            final UniqueId receiptId = receiptIdGenerator.generateId();
            AttributeMapUtil.addReceiptInfo(attributeMap, receiptId);
        }
        // Make sure we have a GUID
        if (NullSafe.isEmptyString(attributeMap.get(StandardHeaderArguments.GUID))) {
            attributeMap.put(StandardHeaderArguments.GUID, UUID.randomUUID().toString());
        }
        return attributeMap;
    }

    private void scanDir(final Path rootDir, final ScanResult scanResult) {
        LOGGER.debug("scanDir() - {}", rootDir);
        final DurationTimer timer = DurationTimer.start();
        final Deque<Path> unknownFiles = new ArrayDeque<>();

        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (isZipFile(file)) {
                        LOGGER.debug("scanDir() - Found ZIP file {}", file);
                        // This will log and swallow, so we can carry on walking
                        processZipFile(file, scanResult);
                    } else if (isSidecarFile(file)) {
                        LOGGER.debug("scanDir() - Found sidecar file {}", file);
                        // This will get handled by processZipFile()
                    } else if (Files.isRegularFile(file)) {
                        LOGGER.debug("scanDir() - Unknown file {}", file);
                        unknownFiles.push(file);
                        scanResult.incrementUnknownCount();
//                        LOGGER.warn("Found file that is not a ZIP file ({}), it will be ignored. " +
//                                    "You should remove this file to stop seeing this message.", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path path, final IOException exc) throws IOException {
                    if (isZipFile(path)) {
                        scanResult.incrementFailCount();
                        LOGGER.error("scanDir() - unable to read zip file {}. Unable to move it to {}. " +
                                     "Might be a permissions issue.: {}",
                                path, failureDir, LogUtil.exceptionMessage(exc));
                    } else {
                        LOGGER.debug(() -> LogUtil.message(
                                "scanDir() - unable to read file/dir {}. This may be because the file has been " +
                                "deleted after successful processing of the associated zip file.: {}",
                                path, LogUtil.exceptionMessage(exc), exc));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    // By this point all the sidecar files should have been dealt with
                    if (!unknownFiles.isEmpty()) {
                        Path unknownFile;
                        while (true) {
                            unknownFile = unknownFiles.poll();
                            if (unknownFile != null) {
                                final Path destPath = moveToFailureDir(unknownFile);
                                LOGGER.info("Found unknown file {}, moved it to {}", unknownFile, destPath);
                            } else {
                                break;
                            }
                        }
                    }
                    return super.postVisitDirectory(dir, exc);
                }
            });
            LOGGER.debug("scanDir() - Scanned {} for ZIP files in {}", rootDir, timer);
        } catch (final Exception e) {
            LOGGER.error("scanDir() - unable to read ZIP file {}: {}",
                    rootDir, LogUtil.exceptionMessage(e));
            // Just log and swallow, so we can move on to the next dir to scan
        } finally {
            // This will not throw
            FileUtil.deleteEmptyDirs(rootDir);
        }
    }

    /**
     * @return The path after the move, or null if it could not be moved.
     */
    private Path moveToFailureDir(final Path sourceFile) {
        final Path destDir = failureDirProvider.createNumberedPath();
        final Path destFile = destDir.resolve(sourceFile.getFileName());
        try {
            Files.move(sourceFile, destFile);
            return destFile;
        } catch (final IOException ex) {
            LOGGER.error("Error moving failed file from {} to {}", sourceFile, destFile, ex);
        }
        return null;
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
        return FileGroup.ZIP_EXTENSION.equalsIgnoreCase(FileNameUtils.getExtension(path))
               && Files.isRegularFile(path);
    }

    private boolean isSidecarFile(final Path path) throws IOException {
        final Path parentDir = path.getParent();
        final String baseName = FileNameUtils.getBaseName(path);
        final String extension = FileNameUtils.getExtension(path);

        if (SIDECAR_FILENAMES.contains(path.getFileName().toString())) {
            // e.g. error.log
            try (final Stream<Path> pathStream = Files.list(parentDir)) {
                final boolean foundMatchingZip = pathStream.filter(Files::isRegularFile)
                        .anyMatch(aPath -> {
                            final String anExtension = FileNameUtils.getExtension(aPath);
                            return FileGroup.ZIP_EXTENSION.equalsIgnoreCase(anExtension);
                        });
                LOGGER.debug("isSidecarFile() - path: {}, foundMatchingZip: {}", path, foundMatchingZip);
                return foundMatchingZip;
            }
        } else if (SIDECAR_EXTENSIONS.contains(extension)) {
            // e.g. proxy.meta, proxy.entries
            try (final Stream<Path> pathStream = Files.list(parentDir)) {
                final boolean foundMatchingZip = pathStream.filter(Files::isRegularFile)
                        .filter(aPath -> {
                            final String aBaseName = FileNameUtils.getBaseName(aPath);
                            return Objects.equals(aBaseName, baseName);
                        })
                        .anyMatch(aPath -> {
                            final String anExtension = FileNameUtils.getExtension(aPath);
                            return FileGroup.ZIP_EXTENSION.equalsIgnoreCase(anExtension);
                        });
                LOGGER.debug("isSidecarFile() - path: {}, foundMatchingZip: {}", path, foundMatchingZip);
                return foundMatchingZip;
            }
        } else {
            return false;
        }
    }

    private List<Path> getPathsToScan(final DirScannerConfig dirScannerConfig) {

        return NullSafe.stream(dirScannerConfig.getDirs())
                .map(pathCreator::toAppPath)
                .filter(path -> {
                    try {
                        FileUtil.ensureDirExists(path);
                    } catch (final Exception e) {
                        LOGGER.error("Error ensuring directory {} exists - {}",
                                path, LogUtil.exceptionMessage(e), e);
                        return false;
                    }
                    return true;
                })
                .toList();
    }


    // --------------------------------------------------------------------------------


    private static class ScanResult {

        private int successCount;
        private int failCount;
        private int unknownCount;

        int getTotalCount() {
            return successCount + failCount;
        }

        void incrementSuccessCount() {
            successCount += 1;
        }

        void incrementFailCount() {
            failCount += 1;
        }

        void incrementUnknownCount() {
            unknownCount += 1;
        }

        boolean isEmpty() {
            return successCount == 0
                   && failCount == 0
                   && unknownCount == 0;
        }

        @Override
        public String toString() {
            return "ScanResult{" +
                   "successCount=" + successCount +
                   ", failCount=" + failCount +
                   ", unknownCount=" + unknownCount +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private record ZipGroup(Path zipFile, Path metaFile, Path errorFile, Path entriesFile) {

        private ZipGroup {
            Objects.requireNonNull(zipFile);
        }

        boolean hasMetaFile() {
            return metaFile != null;
        }

        Stream<Path> streamPaths() {
            return Stream.of(zipFile, metaFile, errorFile, entriesFile)
                    .filter(Objects::nonNull)
                    .filter(Files::exists);
        }
    }
}
