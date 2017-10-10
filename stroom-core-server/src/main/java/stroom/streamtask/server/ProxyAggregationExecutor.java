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
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.proxy.repo.RepositoryProcessor;
import stroom.proxy.repo.StroomZipRepository;
import stroom.proxy.repo.StroomZipRepositoryProcessor;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.AsyncTaskHelper;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Task read a proxy repository and sent it to the stream store.
 * </p>
 */
@Component
@Scope(value = StroomScope.TASK)
public class ProxyAggregationExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationExecutor.class);

    private final static int DEFAULT_MAX_AGGREGATION = 10000;
    private final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private final StreamStore streamStore;
    private final FeedService feedService;
    private final MetaDataStatistic metaDataStatistic;
    private final TaskMonitor taskMonitor;
    private final TaskManager taskManager;
    private final String proxyDir;
    private final int threadCount;
    private final int maxAggregation;
    private final long maxStreamSize;
    private final boolean aggregate = true;
    private final StroomZipRepositoryProcessor stroomZipRepositoryProcessor;

    private Task<?> task;
    private volatile boolean stop = false;

    @Inject
    public ProxyAggregationExecutor(final StreamStore streamStore,
                                    @Named("cachedFeedService") final FeedService feedService,
                                    final MetaDataStatistic metaDataStatistic,
                                    final TaskMonitor taskMonitor,
                                    final TaskManager taskManager,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan) {
        this(
                streamStore,
                feedService,
                metaDataStatistic,
                taskMonitor,
                taskManager,
                proxyDir,
                PropertyUtil.toInt(threadCount, 10),
                PropertyUtil.toInt(maxAggregation, DEFAULT_MAX_AGGREGATION),
                PropertyUtil.toLong(maxStreamSize, DEFAULT_MAX_STREAM_SIZE),
                PropertyUtil.toInt(maxFileScan, RepositoryProcessor.DEFAULT_MAX_FILE_SCAN)
        );
    }

    ProxyAggregationExecutor(final StreamStore streamStore,
                                    final FeedService feedService,
                                    final MetaDataStatistic metaDataStatistic,
                                    final TaskMonitor taskMonitor,
                                    final TaskManager taskManager,
                                    final String proxyDir,
                                    final int threadCount,
                                    final int maxAggregation,
                                    final long maxStreamSize,
                                    final int maxFileScan) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.metaDataStatistic = metaDataStatistic;
        this.taskMonitor = taskMonitor;
        this.taskManager = taskManager;
        this.proxyDir = proxyDir;
        this.threadCount = threadCount;
        this.stroomZipRepositoryProcessor = getStroomZipRepositoryProcessor();
        this.maxAggregation = maxAggregation;
        this.maxStreamSize = maxStreamSize;
        stroomZipRepositoryProcessor.setMaxFileScan(maxFileScan);
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        try {
            this.task = task;
            taskMonitor.addTerminateHandler(this::stop);

            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("exec() - started");

            boolean complete = false;
            while (!complete && !taskMonitor.isTerminated()) {
                taskMonitor.info("Aggregate started {}, maxAggregation {}, maxAggregationScan {}, maxStreamSize {}",
                        DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                        ModelStringUtil.formatCsv(maxAggregation),
                        ModelStringUtil.formatCsv(stroomZipRepositoryProcessor.getMaxFileScan()),
                        ModelStringUtil.formatIECByteSizeString(maxStreamSize));

                final StroomZipRepository stroomZipRepository = new StroomZipRepository(this.proxyDir);
                complete = stroomZipRepositoryProcessor.process(stroomZipRepository);
            }

            LOGGER.info("exec() - completed in {}", logExecutionTime);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private List<StreamTargetStroomStreamHandler> openStreamHandlers(final Feed feed) {
        // We don't want to aggregate reference feeds.
        final boolean oneByOne = feed.isReference() || !aggregate;

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedService, metaDataStatistic, feed, feed.getStreamType());

        streamTargetStroomStreamHandler.setOneByOne(oneByOne);

        final MetaMap globalMetaMap = new MetaMap();
        globalMetaMap.put(StroomHeaderArguments.FEED, feed.getName());

        try {
            streamTargetStroomStreamHandler.handleHeader(globalMetaMap);
        } catch (final IOException ioEx) {
            streamTargetStroomStreamHandler.close();
            throw new RuntimeException(ioEx);
        }

        final List<StreamTargetStroomStreamHandler> list = new ArrayList<>();
        list.add(streamTargetStroomStreamHandler);

        return list;
    }

    private List<StreamTargetStroomStreamHandler> closeStreamHandlers(final List<StreamTargetStroomStreamHandler> handlers) {
        if (handlers != null) {
            handlers.forEach(StreamTargetStroomStreamHandler::close);
        }
        return null;
    }

    private List<StreamTargetStroomStreamHandler> closeDeleteStreamHandlers(
            final List<StreamTargetStroomStreamHandler> handlers) {
        if (handlers != null) {
            handlers.forEach(StreamTargetStroomStreamHandler::closeDelete);
        }
        return null;
    }

    private StroomZipRepositoryProcessor getStroomZipRepositoryProcessor() {
        return new StroomZipRepositoryProcessor(taskMonitor) {
            private volatile AsyncTaskHelper<VoidResult> taskPool;

            @Override
            public void processFeedFiles(final StroomZipRepository stroomZipRepository, final String feedName, final List<Path> fileList) {
                final Feed feed = feedService.loadByName(feedName);

                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("processFeedFiles() - Started {} ({} Files)", feedName, fileList.size());

                if (feed == null) {
                    LOGGER.error("processFeedFiles() - " + feedName + " Failed to find feed");
                    return;
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("processFeedFiles() - " + feedName + " " + fileList);
                }

                // We don't want to aggregate reference feeds.
                final boolean oneByOne = feed.isReference() || !aggregate;

                List<StreamTargetStroomStreamHandler> handlers = openStreamHandlers(feed);
                List<Path> deleteFileList = new ArrayList<>();

                long sequence = 1;
                long maxAggregation = ProxyAggregationExecutor.this.maxAggregation;
                if (oneByOne) {
                    maxAggregation = 1;
                }

                Long nextBatchBreak = ProxyAggregationExecutor.this.maxStreamSize;

                final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor("ProxyAggregationTask");

                for (final Path file : fileList) {
                    if (stop) {
                        break;
                    }
                    try {
                        if (sequence > maxAggregation
                                || (streamProgressMonitor.getTotalBytes() > nextBatchBreak)) {
                            LOGGER.info("processFeedFiles() - Breaking Batch {} as limit is ({} > {}) or ({} > {})",
                                    feedName,
                                    sequence,
                                    maxAggregation,
                                    streamProgressMonitor.getTotalBytes(),
                                    nextBatchBreak
                            );

                            // Recalculate the next batch break
                            nextBatchBreak = streamProgressMonitor.getTotalBytes() + maxStreamSize;

                            // Close off this unit
                            handlers = closeStreamHandlers(handlers);

                            // Delete the done files
                            deleteFiles(stroomZipRepository, deleteFileList);

                            // Start new batch
                            deleteFileList = new ArrayList<>();
                            handlers = openStreamHandlers(feed);
                            sequence = 1;
                        }
                        sequence = processFeedFile(handlers, stroomZipRepository, file, streamProgressMonitor, sequence);
                        deleteFileList.add(file);

                    } catch (final Throwable t) {
                        handlers = closeDeleteStreamHandlers(handlers);
                    }
                }
                closeStreamHandlers(handlers);
                deleteFiles(stroomZipRepository, deleteFileList);
                LOGGER.info("processFeedFiles() - Completed {} in {}", feedName, logExecutionTime);
            }

            @Override
            public void startExecutor() {
                taskPool = new AsyncTaskHelper<>(null, taskMonitor, taskManager, threadCount);
            }

            @Override
            public void stopExecutor(final boolean now) {
                if (taskPool != null) {
                    if (now) {
                        taskMonitor.terminate();
                        taskPool.clear();
                    }
                    taskPool.join();
                }
            }

            @Override
            public void waitForComplete() {
                taskPool.join();
            }

            @Override
            public void execute(final String message, final Runnable runnable) {
                if (!stop) {
                    final GenericServerTask genericServerTask = GenericServerTask.create(task, task.getTaskName(), message);
                    genericServerTask.setRunnable(runnable);
                    taskPool.fork(genericServerTask);
                }
            }
        };
    }

    /**
     * Stops the task as soon as possible.
     */
    private void stop() {
        LOGGER.info("stop() - Proxy Aggregation - Stopping");
        stroomZipRepositoryProcessor.stopExecutor(true);
        stop = true;
    }
}
