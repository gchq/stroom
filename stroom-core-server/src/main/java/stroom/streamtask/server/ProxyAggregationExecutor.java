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
 *
 */

package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.StroomHeaderArguments;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipRepository;
import stroom.streamtask.server.FileWalker.FileFilter;
import stroom.streamtask.server.FileWalker.FileProcessor;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Component
@Scope(value = StroomScope.PROTOTYPE)
public class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private static final int DEFAULT_MAX_AGGREGATION = 10000;
    private static final long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");
    private static final int DEFAULT_MAX_FILE_SCAN = 10000;
    private static final FileFilter ZIP_FILTER = (file, attrs) -> file.toString().endsWith(StroomZipRepository.ZIP_EXTENSION);

    private final TaskContext taskContext;
    private final ExecutorProvider executorProvider;
    private final Provider<FilePackProcessor> filePackProcessorProvider;

    private final int threadCount;
    private final int maxFilesPerAggregate;
    private final int maxConcurrentMappedFiles;
    private final long maxUncompressedFileSize;

    private final StroomZipRepository stroomZipRepository;
    
    @Inject
    public ProxyAggregationExecutor(final TaskContext taskContext,
                                    final ExecutorProvider executorProvider,
                                    final Provider<FilePackProcessor> filePackProcessorProvider,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxFilesPerAggregate,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxConcurrentMappedFiles,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxUncompressedFileSize) {
        this(
                taskContext,
                executorProvider,
                filePackProcessorProvider,
                proxyDir,
                PropertyUtil.toInt(threadCount, 10),
                PropertyUtil.toInt(maxFilesPerAggregate, DEFAULT_MAX_AGGREGATION),
                PropertyUtil.toInt(maxConcurrentMappedFiles, DEFAULT_MAX_FILE_SCAN),
                getByteSize(maxUncompressedFileSize, DEFAULT_MAX_STREAM_SIZE)
        );
    }

    ProxyAggregationExecutor(final TaskContext taskContext,
                             final ExecutorProvider executorProvider,
                             final Provider<FilePackProcessor> filePackProcessorProvider,
                             final String proxyDir,
                             final int threadCount,
                             final int maxFilesPerAggregate,
                             final int maxConcurrentMappedFiles,
                             final long maxUncompressedFileSize) {
        this.taskContext = taskContext;
        this.executorProvider = executorProvider;
        this.filePackProcessorProvider = filePackProcessorProvider;
        this.threadCount = threadCount;
        this.maxFilesPerAggregate = maxFilesPerAggregate;
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
        this.maxUncompressedFileSize = maxUncompressedFileSize;

        stroomZipRepository = new StroomZipRepository(proxyDir);
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        if (!taskContext.isTerminated()) {
            try {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("exec() - started");

                taskContext.info("Aggregate started {}, maxFilesPerAggregate {}, maxConcurrentMappedFiles {}, maxUncompressedFileSize {}",
                        DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                        ModelStringUtil.formatCsv(maxFilesPerAggregate),
                        ModelStringUtil.formatCsv(maxConcurrentMappedFiles),
                        ModelStringUtil.formatIECByteSizeString(maxUncompressedFileSize));

                process(stroomZipRepository);

                LOGGER.info("exec() - completed in {}", logExecutionTime);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Process a Stroom zip repository,
     *
     * @param stroomZipRepository The Stroom zip repository to process.
     */
    private void process(final StroomZipRepository stroomZipRepository) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("process() - Scanning " + stroomZipRepository.getRootDir());
            }

            // Scan all of the zip files in the repository so that we can map zip files to feeds.
            final ErrorReceiver errorReceiver = (path, message) -> addErrorMessage(path, message, true);
            final ZipInfoConsumer zipInfoConsumer = new ZipInfoConsumer(
                    stroomZipRepository,
                    maxFilesPerAggregate,
                    maxConcurrentMappedFiles,
                    maxUncompressedFileSize,
                    errorReceiver,
                    filePackProcessorProvider,
                    executorProvider,
                    threadCount);
            final ZipInfoExtractor zipInfoExtractor = new ZipInfoExtractor(errorReceiver);
            final FileProcessorImpl fileProcessor = new FileProcessorImpl(
                    zipInfoExtractor,
                    zipInfoConsumer,
                    taskContext,
                    executorProvider,
                    threadCount);
            final FileWalker fileWalker = new FileWalker();
            fileWalker.walk(stroomZipRepository.getRootDir(), ZIP_FILTER, fileProcessor, taskContext);

            // Wait for the file processor to complete.
            fileProcessor.await();

            // Complete processing remaining file packs.
            zipInfoConsumer.complete();

            LOGGER.debug("Completed");
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
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

    private static long getByteSize(final String propertyValue, final long defaultValue) {
        Long value = null;
        try {
            value = ModelStringUtil.parseIECByteSizeString(propertyValue);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (value == null) {
            value = defaultValue;
        }

        return value;
    }

    private static class ZipInfoConsumer implements Consumer<ZipInfo> {
        private final StroomZipRepository stroomZipRepository;
        private final int maxFilesPerAggregate;
        private final int maxConcurrentMappedFiles;
        private final long maxUncompressedFileSize;
        private final ErrorReceiver errorReceiver;
        private final Provider<FilePackProcessor> filePackProcessorProvider;
        private final Executor executor;

        private final Map<String, FilePack> filePackMap = new HashMap<>();
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private int totalMappedFiles;

        ZipInfoConsumer(final StroomZipRepository stroomZipRepository,
                        final int maxFilesPerAggregate,
                        final int maxConcurrentMappedFiles,
                        final long maxUncompressedFileSize,
                        final ErrorReceiver errorReceiver,
                        final Provider<FilePackProcessor> filePackProcessorProvider,
                        final ExecutorProvider executorProvider,
                        final int threadCount) {
            this.stroomZipRepository = stroomZipRepository;
            this.maxFilesPerAggregate = maxFilesPerAggregate;
            this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
            this.maxUncompressedFileSize = maxUncompressedFileSize;
            this.errorReceiver = errorReceiver;
            this.filePackProcessorProvider = filePackProcessorProvider;

            final ThreadPool fileInspectorThreadPool = new ThreadPoolImpl("File Pack Processor", 5, 0, threadCount);
            executor = executorProvider.getExecutor(fileInspectorThreadPool);
        }

        @Override
        public void accept(final ZipInfo zipInfo) {
            final String feedName = zipInfo.getMetaMap().get(StroomHeaderArguments.FEED);
            if (feedName == null || feedName.length() == 0) {
                errorReceiver.onError(zipInfo.getPath(), "Unable to find feed in header??");

            } else {
                LOGGER.debug("{} belongs to feed {}", zipInfo.getPath(), feedName);

                FilePack filePack = filePackMap.computeIfAbsent(feedName, k -> new FilePack(feedName));

                // See if the file pack will be full if we add this file.
                if (filePack.getFiles().size() > 0 &&
                        (filePack.getTotalUncompressedFileSize() + zipInfo.getUncompressedSize() > maxUncompressedFileSize ||
                                filePack.getFiles().size() >= maxFilesPerAggregate)) {

                    // Send the file pack for processing.
                    processFilePack(filePack);

                    // Create a new file pack.
                    filePack = filePackMap.computeIfAbsent(feedName, k -> new FilePack(feedName));
                }

                // The file pack is not full so add the file.
                filePack.add(zipInfo);
                totalMappedFiles++;

                // If the file pack is now full send it for processing.
                if (filePack.getFiles().size() >= maxFilesPerAggregate) {

                    // Send the file pack for processing.
                    processFilePack(filePack);

                } else {

                    // If we have reached the maximum number of concurrent mapped files then we need to send the largest pack for processing.
                    if (totalMappedFiles >= maxConcurrentMappedFiles) {
                        final List<FilePack> sortedList = filePackMap
                                .values()
                                .stream()
                                .sorted(Comparator.comparing(FilePack::getTotalUncompressedFileSize).reversed())
                                .collect(Collectors.toList());

                        // Remove the biggest file pack from the map.
                        final FilePack biggestFilePack = sortedList.get(0);

                        // Send the file pack for processing.
                        processFilePack(biggestFilePack);
                    }
                }
            }
        }

        private void processFilePack(final FilePack filePack) {
            // Remove the full file pack.
            filePackMap.remove(filePack.getFeed());

            // Reduce the total number of files we have mapped.
            totalMappedFiles -= filePack.getFiles().size();

            try {
                final Runnable runnable = () -> {
                    final FilePackProcessor filePackProcessor = filePackProcessorProvider.get();
                    filePackProcessor.process(stroomZipRepository, filePack);
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFuture.thenAccept(r -> futures.remove(completableFuture));
                futures.add(completableFuture);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        public void complete() {
            // Send all remaining file packs for processing.
            filePackMap.values().forEach(this::processFilePack);

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    private static class FileProcessorImpl implements FileProcessor {
        private final ZipInfoExtractor zipInfoExtractor;
        private final TaskContext taskContext;
        private final Executor executor;
        private final Consumer<ZipInfo> zipInfoConsumer;
        private final Set<CompletableFuture> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());

        FileProcessorImpl(final ZipInfoExtractor zipInfoExtractor,
                          final Consumer<ZipInfo> zipInfoConsumer,
                          final TaskContext taskContext,
                          final ExecutorProvider executorProvider,
                          final int threadCount) {
            this.zipInfoExtractor = zipInfoExtractor;
            this.zipInfoConsumer = zipInfoConsumer;
            this.taskContext = taskContext;

            final ThreadPool fileInspectorThreadPool = new ThreadPoolImpl("Proxy File Inspection", 5, 0, threadCount);
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
        private synchronized void processZipInfo(final ZipInfo zipInfo) {
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
