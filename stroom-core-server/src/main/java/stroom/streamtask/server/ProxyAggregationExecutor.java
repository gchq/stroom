/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.statistic.server.MetaDataStatistic;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.AsyncTaskHelper;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.config.PropertyUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomZipRepository;
import stroom.util.zip.StroomZipRepositoryProcessor;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
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
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ProxyAggregationExecutor.class);

    private final StreamStore streamStore;
    private final FeedService feedService;
    private final MetaDataStatistic metaDataStatistic;
    private final TaskMonitor taskMonitor;
    private final TaskManager taskManager;
    private final ThreadLocalBuffer proxyAggregationThreadLocalBuffer;
    private final int threadCount;
    private final StroomZipRepositoryProcessor stroomZipRepositoryProcessor;

    private AsyncTaskHelper<VoidResult> taskPool;
    private Task<?> task;

    private String proxyDir;
    private boolean aggregate = true;
    private boolean stop = false;

    @Inject
    public ProxyAggregationExecutor(final StreamStore streamStore,
                                    @Named("cachedFeedService") final FeedService feedService,
                                    final MetaDataStatistic metaDataStatistic,
                                    final TaskMonitor taskMonitor,
                                    final TaskManager taskManager,
                                    @Named("prototypeThreadLocalBuffer") final ThreadLocalBuffer proxyAggregationThreadLocalBuffer,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                    @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize,
                                    @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.metaDataStatistic = metaDataStatistic;
        this.taskMonitor = taskMonitor;
        this.taskManager = taskManager;
        this.proxyAggregationThreadLocalBuffer = proxyAggregationThreadLocalBuffer;
        this.proxyDir = proxyDir;
        this.threadCount = PropertyUtil.toInt(threadCount, 10);

        LOGGER.debug("ProxyAggregationExecutor() - new instance");

        this.stroomZipRepositoryProcessor = getStroomZipRepositoryProcessor();
        stroomZipRepositoryProcessor.setMaxAggregation(PropertyUtil.toInt(maxAggregation, StroomZipRepositoryProcessor.DEFAULT_MAX_AGGREGATION));
        stroomZipRepositoryProcessor.setMaxStreamSizeString(maxStreamSize);
        stroomZipRepositoryProcessor.setMaxFileScan(PropertyUtil.toInt(maxFileScan, StroomZipRepositoryProcessor.DEFAULT_MAX_FILE_SCAN));
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "Proxy Aggregation", advanced = false, description = "Job to pick up the data written by the proxy and store it in Stroom")
    public void exec(final Task<?> task) {
        aggregate(task, proxyDir);
    }

    public void aggregate(final Task<?> task, final String proxyDir) {
        aggregate(task, proxyDir, aggregate, null, null);
    }

    public void aggregate(final Task<?> task, final String proxyDir, final Boolean aggregate,
                          final Integer maxAggregation, final Long maxStreamSize) {
        this.task = task;
        if (proxyDir != null) {
            this.proxyDir = proxyDir;
        }
        this.aggregate = aggregate;
        if (maxAggregation != null) {
            stroomZipRepositoryProcessor.setMaxAggregation(maxAggregation);
        }
        if (maxStreamSize != null) {
            stroomZipRepositoryProcessor.setMaxStreamSize(maxStreamSize);
        }

        taskMonitor.addTerminateHandler(() -> stop());

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("exec() - started");

        boolean complete = false;
        while (!complete && !taskMonitor.isTerminated()) {
            taskMonitor.info("Aggregate started %s, maxAggregation %s, maxAggregationScan %s, maxStreamSize %s",
                    DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                    ModelStringUtil.formatCsv(stroomZipRepositoryProcessor.getMaxAggregation()),
                    ModelStringUtil.formatCsv(stroomZipRepositoryProcessor.getMaxFileScan()),
                    ModelStringUtil.formatIECByteSizeString(stroomZipRepositoryProcessor.getMaxStreamSize()));

            final StroomZipRepository stroomZipRepository = new StroomZipRepository(this.proxyDir);
            complete = stroomZipRepositoryProcessor.process(stroomZipRepository);
        }

        LOGGER.info("exec() - completed in %s", logExecutionTime);
    }

    private List<StreamTargetStroomStreamHandler> openStreamHandlers(final Feed feed) {
        // We don't want to aggregate reference feeds.
        final boolean oneByOne = feed.isReference() || !aggregate;

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedService, metaDataStatistic, feed, feed.getStreamType());

        streamTargetStroomStreamHandler.setOneByOne(oneByOne);

        final HeaderMap globalHeaderMap = new HeaderMap();
        globalHeaderMap.put(StroomHeaderArguments.FEED, feed.getName());

        try {
            streamTargetStroomStreamHandler.handleHeader(globalHeaderMap);
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

    protected StroomZipRepositoryProcessor getStroomZipRepositoryProcessor() {
        return new StroomZipRepositoryProcessor(taskMonitor) {
            @Override
            public void processFeedFiles(final StroomZipRepository stroomZipRepository, final String feedName,
                                         final List<File> fileList) {
                final Feed feed = feedService.loadByName(feedName);

                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("processFeedFiles() - Started %s (%s Files)", feedName, fileList.size());

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
                List<File> deleteFileList = new ArrayList<>();

                long sequence = 1;
                long maxAggregation = getMaxAggregation();
                if (oneByOne) {
                    maxAggregation = 1;
                }

                Long nextBatchBreak = getMaxStreamSize();

                final StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor("ProxyAggregationTask");

                for (final File file : fileList) {
                    if (stop) {
                        break;
                    }
                    try {
                        if (sequence > maxAggregation
                                || (nextBatchBreak != null && streamProgressMonitor.getTotalBytes() > nextBatchBreak)) {
                            LOGGER.info("processFeedFiles() - Breaking Batch %s as limit is (%s > %s) or (%s > %s)",
                                    feedName, sequence, maxAggregation, streamProgressMonitor.getTotalBytes(),
                                    nextBatchBreak);

                            // Recalculate the next batch break
                            if (nextBatchBreak != null) {
                                nextBatchBreak = streamProgressMonitor.getTotalBytes() + getMaxStreamSize();
                            }
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
                LOGGER.info("processFeedFiles() - Completed %s in %s", feedName, logExecutionTime);
            }

            @Override
            public byte[] getReadBuffer() {
                return proxyAggregationThreadLocalBuffer.getBuffer();
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
                    final GenericServerTask genericServerTask = new GenericServerTask(task, task.getSessionId(), task.getUserId(),
                            task.getTaskName(), message);
                    genericServerTask.setRunnable(runnable);
                    taskPool.fork(genericServerTask);
                }
            }
        };
    }

    public void setAggregate(final boolean aggregate) {
        this.aggregate = aggregate;
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
