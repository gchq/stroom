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
import stroom.proxy.repo.FileWalker.FileFilter;
import stroom.proxy.repo.FileWalker.FileProcessor;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.date.DateUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ThreadPool;

import javax.inject.Provider;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
    private final int maxFilesPerAggregate;
    private final int maxConcurrentMappedFiles;
    private final long maxUncompressedFileSize;

    private final StroomZipRepository stroomZipRepository;

    public RepositoryProcessor(final TaskContext taskContext,
                               final ExecutorProvider executorProvider,
                               final Provider<FileSetProcessor> fileSetProcessorProvider,
                               final String proxyDir,
                               final int threadCount,
                               final int maxFilesPerAggregate,
                               final int maxConcurrentMappedFiles,
                               final long maxUncompressedFileSize) {
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.fileSetProcessorProvider = fileSetProcessorProvider;
        this.threadCount = threadCount;
        this.maxFilesPerAggregate = maxFilesPerAggregate;
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
        this.maxUncompressedFileSize = maxUncompressedFileSize;

        stroomZipRepository = new StroomZipRepository(proxyDir, true);
    }

    /**
     * Process the Stroom zip repository,
     */
    public void process() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("Started");

        taskContext.info("Process started {}, maxFilesPerAggregate {}, " +
                        "maxConcurrentMappedFiles {}, maxUncompressedFileSize {}",
                DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                ModelStringUtil.formatCsv(maxFilesPerAggregate),
                ModelStringUtil.formatCsv(maxConcurrentMappedFiles),
                ModelStringUtil.formatIECByteSizeString(maxUncompressedFileSize));

        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Scanning " + stroomZipRepository.getRootDir());
            }

            final ErrorReceiver errorReceiver = (path, message) ->
                    addErrorMessage(path, message, true);

            // Break down the zip repository so that all zip files only contain a single stream.
            // We do this so that we can form new aggregates that contain less files than the
            // maximum number or are smaller than the maximum size
            fragmentZipFiles(taskContext, executorProvider, threadCount, errorReceiver);

            // Aggregate the zip files.
            aggregateZipFiles(taskContext, executorProvider, threadCount, errorReceiver);

            LOGGER.debug("Completed");
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        LOGGER.info("Completed in {}", logExecutionTime);
    }

    private void fragmentZipFiles(final TaskContext taskContext,
                                  final ExecutorProvider executorProvider,
                                  final int threadCount,
                                  final ErrorReceiver errorReceiver) {
        // Process only raw zip part files, i.e. files that have not already been created by the fragmenting process.
        final FileFilter filter = (file, attrs) -> {
            final String fileName = file.getFileName().toString();
            return fileName.endsWith(StroomZipRepository.ZIP_EXTENSION) && !fileName.contains(PathConstants.PART);
        };

        final ZipFragmenterFileProcessor zipFragmenter = new ZipFragmenterFileProcessor(
                taskContext, executorProvider, threadCount, errorReceiver);

        final FileWalker fileWalker = new FileWalker();
        fileWalker.walk(stroomZipRepository.getRootDir(), filter, zipFragmenter, taskContext);

        // Wait for the fragmenter to complete.
        zipFragmenter.await();
    }

    private void aggregateZipFiles(final TaskContext taskContext,
                                   final ExecutorProvider executorProvider,
                                   final int threadCount,
                                   final ErrorReceiver errorReceiver) {
        // Process only part files, i.e. files that have been created by the fragmenting process.
        final FileFilter filter = (file, attrs) -> {
            final String fileName = file.getFileName().toString();
            return fileName.endsWith(StroomZipRepository.ZIP_EXTENSION) && fileName.contains(PathConstants.PART);
        };

        final ZipInfoConsumer zipInfoConsumer = new ZipInfoConsumer(
                stroomZipRepository,
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
        final FileWalker fileWalker = new FileWalker();
        fileWalker.walk(stroomZipRepository.getRootDir(), filter, fileProcessor, taskContext);

        // Wait for the file processor to complete.
        fileProcessor.await();

        // Complete processing remaining file sets.
        zipInfoConsumer.complete();
    }

    private void addErrorMessage(final Path path, final String msg, final boolean bad) {
        StroomZipFile stroomZipFile = null;
        try {
            stroomZipFile = new StroomZipFile(path);
            stroomZipRepository.addErrorMessage(stroomZipFile, msg, bad);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(stroomZipFile);
        }
    }

    private static class ZipInfoConsumer implements Consumer<ZipInfo> {
        private final StroomZipRepository stroomZipRepository;
        private final int maxFilesPerAggregate;
        private final int maxConcurrentMappedFiles;
        private final long maxUncompressedFileSize;
        private final ErrorReceiver errorReceiver;
        private final Provider<FileSetProcessor> fileSetProcessorProvider;
        private final Executor executor;

        private final Map<String, FileSet> fileSetMap = new ConcurrentHashMap<>();
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private int totalMappedFiles;

        ZipInfoConsumer(final StroomZipRepository stroomZipRepository,
                        final int maxFilesPerAggregate,
                        final int maxConcurrentMappedFiles,
                        final long maxUncompressedFileSize,
                        final ErrorReceiver errorReceiver,
                        final Provider<FileSetProcessor> fileSetProcessorProvider,
                        final ExecutorProvider executorProvider,
                        final int threadCount) {
            this.stroomZipRepository = stroomZipRepository;
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
                    fileSetProcessor.process(stroomZipRepository, fileSet);
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenAccept(r -> futures.remove(completableFuture));
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

    private static class ZipFragmenterFileProcessor implements FileProcessor {
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

        @Override
        public void process(final Path file, final BasicFileAttributes attrs) {
            try {
                final Runnable runnable = () -> {
                    // Process the file to extract ZipInfo
                    taskContext.setName("Fragment");
                    taskContext.info(FileUtil.getCanonicalPath(file));

                    if (!taskContext.isTerminated()) {
                        zipFragmenter.fragment(file, attrs);
                    }
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenAccept(r -> futures.remove(completableFuture));
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

    private static class ZipInfoExtractorFileProcessor implements FileProcessor {
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

        @Override
        public void process(final Path file, final BasicFileAttributes attrs) {
            try {
                final Runnable runnable = () -> {
                    // Process the file to extract ZipInfo
                    taskContext.setName("Extract Zip Info");
                    taskContext.info(FileUtil.getCanonicalPath(file));

                    if (!taskContext.isTerminated()) {
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
                completableFuture.thenAccept(r -> futures.remove(completableFuture));
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
