/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.shared.ThreadPool;
import stroom.task.shared.ThreadPoolImpl;
import stroom.util.date.DateUtil;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Provider;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public final class RepositoryProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryProcessor.class);

    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final Provider<FileSetProcessor> fileSetProcessorProvider;

    private final int threadCount;
    private final int maxFileScan;
    private final int maxConcurrentMappedFiles;
    private final int maxFilesPerAggregate;
    private final long maxUncompressedFileSize;
    private final Path repoPath;
    private final String repoDir;

    public RepositoryProcessor(final TaskContext taskContext,
                               final ExecutorProvider executorProvider,
                               final Provider<FileSetProcessor> fileSetProcessorProvider,
                               final String proxyDir,
                               final int threadCount,
                               final int maxFileScan,
                               final int maxConcurrentMappedFiles,
                               final int maxFilesPerAggregate,
                               final long maxUncompressedFileSize) {
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.fileSetProcessorProvider = fileSetProcessorProvider;
        this.threadCount = threadCount;
        this.maxFileScan = maxFileScan;
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
        this.maxFilesPerAggregate = maxFilesPerAggregate;
        this.maxUncompressedFileSize = maxUncompressedFileSize;
        repoPath = Paths.get(proxyDir);
        repoDir = FileUtil.getCanonicalPath(repoPath);
    }

    /**
     * Process the Stroom zip repository,
     */
    public void process() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("Started");

        taskContext.info(LambdaLogUtil.message("Process started {}, maxFileScan {}, maxConcurrentMappedFiles {}, maxFilesPerAggregate {}, maxUncompressedFileSize {}",
                DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                ModelStringUtil.formatCsv(maxFileScan),
                ModelStringUtil.formatCsv(maxConcurrentMappedFiles),
                ModelStringUtil.formatCsv(maxFilesPerAggregate),
                ModelStringUtil.formatIECByteSizeString(maxUncompressedFileSize)));

        try {
            if (Files.isDirectory(repoPath)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Scanning " + repoDir);
                }

                final ErrorReceiver errorReceiver = (path, message) ->
                        addErrorMessage(path, message, true);

                // Keep processing until we no longer reach the maximum file scan limit.
                boolean reachedFileScanLimit;
                do {
                    // Break down the zip repository so that all zip files only contain a single stream.
                    // We do this so that we can form new aggregates that contain less files than the
                    // maximum number or are smaller than the maximum size
                    reachedFileScanLimit = fragmentZipFiles(taskContext, executorProvider, threadCount, errorReceiver);

                    // Aggregate the zip files.
                    aggregateZipFiles(taskContext, executorProvider, threadCount, errorReceiver);

                } while (reachedFileScanLimit);

                LOGGER.debug("Completed");

            } else {
                LOGGER.debug("Repo dir " + repoDir + " not found");
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        LOGGER.info("Completed in {}", logExecutionTime);
    }

    private boolean fragmentZipFiles(final TaskContext taskContext,
                                     final ExecutorProvider executorProvider,
                                     final int threadCount,
                                     final ErrorReceiver errorReceiver) {
        taskContext.setName("Fragmenting Repository - " + repoDir);

        final ZipFragmenterFileProcessor zipFragmenter = new ZipFragmenterFileProcessor(
                taskContext, executorProvider, threadCount, errorReceiver);

        final AtomicInteger fileCount = new AtomicInteger();
        try {
            Files.walkFileTree(repoPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (PartsPathUtil.isPartsDir(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    taskContext.info(() -> FileUtil.getCanonicalPath(file));

                    if (Thread.currentThread().isInterrupted() || fileCount.get() >= maxFileScan) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Process only raw zip repo files, i.e. files that have not already been created by the fragmenting process.
                    final String fileName = file.getFileName().toString();
                    if (fileName.endsWith(StroomZipRepository.ZIP_EXTENSION) &&
                            !PartsPathUtil.isPart(file)) {
                        fileCount.incrementAndGet();
                        zipFragmenter.process(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Wait for the fragmenter to complete.
        zipFragmenter.await();

        // Did we reach the file scan limit?
        return fileCount.get() >= maxFileScan;
    }

    private void aggregateZipFiles(final TaskContext taskContext,
                                   final ExecutorProvider executorProvider,
                                   final int threadCount,
                                   final ErrorReceiver errorReceiver) {
        taskContext.setName("Aggregating Repository - " + repoDir);
        final ZipInfoConsumer zipInfoConsumer = new ZipInfoConsumer(
                maxFilesPerAggregate,
                maxConcurrentMappedFiles,
                maxUncompressedFileSize,
                errorReceiver,
                fileSetProcessorProvider,
                executorProvider,
                threadCount);
        final ZipInfoExtractor zipInfoExtractor = new ZipInfoExtractor(errorReceiver);
        final ZipInfoExtractorFileProcessor fileProcessor = new ZipInfoExtractorFileProcessor(
                zipInfoExtractor,
                zipInfoConsumer,
                taskContext,
                executorProvider,
                threadCount);

        try {
            Files.walkFileTree(repoPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (PartsPathUtil.isPartsDir(dir)) {
                        final Path originalZipFile = PartsPathUtil.createParentPartsZipFile(dir);

                        // Make sure the parts directory that this file is in is not a partially fragmented output directory.
                        if (Files.exists(originalZipFile)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    taskContext.info(() -> FileUtil.getCanonicalPath(file));

                    if (Thread.currentThread().isInterrupted()) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Process only part files, i.e. files that have been created by the fragmenting process.
                    if (PartsPathUtil.isPart(file)) {
                        fileProcessor.process(file, attrs);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Wait for the file processor to complete.
        fileProcessor.await();

        // Complete processing remaining file sets.
        zipInfoConsumer.complete();
    }

    private void addErrorMessage(final Path path, final String msg, final boolean bad) {
        ErrorFileUtil.addErrorMessage(path, msg, bad);
    }

    private static class ZipInfoConsumer implements Consumer<ZipInfo> {
        private final int maxFilesPerAggregate;
        private final int maxConcurrentMappedFiles;
        private final long maxUncompressedFileSize;
        private final ErrorReceiver errorReceiver;
        private final Provider<FileSetProcessor> fileSetProcessorProvider;
        private final Executor executor;

        private final Map<String, FileSet> fileSetMap = new ConcurrentHashMap<>();
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private int totalMappedFiles;

        ZipInfoConsumer(final int maxFilesPerAggregate,
                        final int maxConcurrentMappedFiles,
                        final long maxUncompressedFileSize,
                        final ErrorReceiver errorReceiver,
                        final Provider<FileSetProcessor> fileSetProcessorProvider,
                        final ExecutorProvider executorProvider,
                        final int threadCount) {
            this.maxFilesPerAggregate = maxFilesPerAggregate;
            this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
            this.maxUncompressedFileSize = maxUncompressedFileSize;
            this.errorReceiver = errorReceiver;
            this.fileSetProcessorProvider = fileSetProcessorProvider;

            final ThreadPool threadPool = new ThreadPoolImpl(
                    "File Set Processor",
                    5,
                    0,
                    threadCount,
                    2 * threadCount);
            executor = executorProvider.getExecutor(threadPool);
        }

        @Override
        public synchronized void accept(final ZipInfo zipInfo) {
            final String feedName = zipInfo.getFeedName();
            if (feedName == null || feedName.length() == 0) {
                errorReceiver.onError(zipInfo.getPath(), "Unable to find feed in header??");

            } else {
                LOGGER.debug("{} belongs to feed {}", zipInfo.getPath(), feedName);

                FileSet fileSet = fileSetMap.computeIfAbsent(feedName, k -> new FileSet(feedName));

                // See if the file set will overflow if we add this file.
                if (fileSet.getFiles().size() > 0 &&
                        (fileSet.getTotalUncompressedFileSize() + zipInfo.getUncompressedSize() > maxUncompressedFileSize ||
                                fileSet.getTotalZipEntryCount() + zipInfo.getZipEntryCount() > maxFilesPerAggregate)) {

                    // Send the file set for processing.
                    processFileSet(fileSet);

                    // Create a new file set.
                    fileSet = fileSetMap.computeIfAbsent(feedName, k -> new FileSet(feedName));
                }

                // The file set is not full so add the file.
                fileSet.add(zipInfo);
                totalMappedFiles++;

                // If the file set is now full send it for processing.
                if (fileSet.getTotalUncompressedFileSize() >= maxUncompressedFileSize ||
                        fileSet.getTotalZipEntryCount() >= maxFilesPerAggregate) {

                    // Send the file set for processing.
                    processFileSet(fileSet);

                } else {

                    // If we have reached the maximum number of concurrent mapped files then we need to send the largest set for processing.
                    if (totalMappedFiles >= maxConcurrentMappedFiles) {
                        final List<FileSet> sortedList = fileSetMap
                                .values()
                                .stream()
                                .sorted(Comparator.comparing(FileSet::getTotalUncompressedFileSize).reversed())
                                .collect(Collectors.toList());

                        // Remove the biggest file set from the map.
                        final FileSet biggestFileSet = sortedList.get(0);

                        // Send the file set for processing.
                        processFileSet(biggestFileSet);
                    }
                }
            }
        }

        private synchronized void processFileSet(final FileSet fileSet) {
            // Remove the full file set.
            fileSetMap.remove(fileSet.getFeed());

            // Reduce the total number of files we have mapped.
            totalMappedFiles -= fileSet.getFiles().size();

            try {
                final Runnable runnable = () -> {
                    final FileSetProcessor fileSetProcessor = fileSetProcessorProvider.get();
                    fileSetProcessor.process(fileSet);
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenRun(() -> futures.remove(completableFuture));
                futures.add(completableFuture);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        public synchronized void complete() {
            // Send all remaining file sets for processing.
            fileSetMap.values().forEach(this::processFileSet);

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    private static class ZipFragmenterFileProcessor {
        private final ZipFragmenter zipFragmenter;
        private final TaskContext taskContext;
        private final Executor executor;
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        ZipFragmenterFileProcessor(final TaskContext taskContext,
                                   final ExecutorProvider executorProvider,
                                   final int threadCount,
                                   final ErrorReceiver errorReceiver) {
            this.taskContext = taskContext;

            final ThreadPool fileInspectorThreadPool = new ThreadPoolImpl(
                    "Proxy File Fragmenter",
                    5,
                    0,
                    threadCount,
                    2 * threadCount);
            executor = executorProvider.getExecutor(fileInspectorThreadPool);

            zipFragmenter = new ZipFragmenter(errorReceiver);
        }

        public void process(final Path file) {
            try {
                final Runnable runnable = () -> {
                    // Process the file to extract ZipInfo
                    taskContext.setName("Fragment");
                    taskContext.info(() -> FileUtil.getCanonicalPath(file));

                    if (!Thread.currentThread().isInterrupted()) {
                        zipFragmenter.fragment(file);
                    }
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenRun(() -> futures.remove(completableFuture));
                futures.add(completableFuture);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        public void await() {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    private static class ZipInfoExtractorFileProcessor {
        private final ZipInfoExtractor zipInfoExtractor;
        private final TaskContext taskContext;
        private final Executor executor;
        private final Consumer<ZipInfo> zipInfoConsumer;
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        ZipInfoExtractorFileProcessor(final ZipInfoExtractor zipInfoExtractor,
                                      final Consumer<ZipInfo> zipInfoConsumer,
                                      final TaskContext taskContext,
                                      final ExecutorProvider executorProvider,
                                      final int threadCount) {
            this.zipInfoExtractor = zipInfoExtractor;
            this.zipInfoConsumer = zipInfoConsumer;
            this.taskContext = taskContext;

            final ThreadPool fileInspectorThreadPool = new ThreadPoolImpl("Proxy File Inspection", 5, 0, threadCount, 2 * threadCount);
            executor = executorProvider.getExecutor(fileInspectorThreadPool);
        }

        public void process(final Path file, final BasicFileAttributes attrs) {
            try {
                final Runnable runnable = () -> {
                    // Process the file to extract ZipInfo
                    taskContext.setName("Extract Zip Info");
                    taskContext.info(() -> FileUtil.getCanonicalPath(file));

                    if (!Thread.currentThread().isInterrupted()) {
                        final ZipInfo zipInfo = zipInfoExtractor.extract(file, attrs);
                        if (zipInfo != null) {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Consume: " + zipInfo);
                            }
                            processZipInfo(zipInfo);
                        }
                    }
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenRun(() -> futures.remove(completableFuture));
                futures.add(completableFuture);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        /**
         * This method is synchronised so that the zip info consumer can operate in a thread safe manner.
         *
         * @param zipInfo The zip info to process.
         */
        private void processZipInfo(final ZipInfo zipInfo) {
            zipInfoConsumer.accept(zipInfo);
        }

        public void await() {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }
    }
}
