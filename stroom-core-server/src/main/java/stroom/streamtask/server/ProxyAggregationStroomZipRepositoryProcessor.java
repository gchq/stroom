package stroom.streamtask.server;

import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.statistic.server.MetaDataStatistic;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskContext;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomZipRepository;
import stroom.util.zip.StroomZipRepositoryProcessor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class ProxyAggregationStroomZipRepositoryProcessor extends StroomZipRepositoryProcessor {

    public static final StroomLogger LOGGER = StroomLogger.getLogger(ProxyAggregationStroomZipRepositoryProcessor.class);

    private final StreamStore streamStore;
    private final MetaDataStatistic metaDataStatistic;
    private final FeedService feedService;
    private final ThreadLocalBuffer proxyAggregationThreadLocalBuffer;
    private final TaskContext taskContext;

    private final boolean aggregate;

    ProxyAggregationStroomZipRepositoryProcessor(final StreamStore streamStore,
                                                 final MetaDataStatistic metaDataStatistic,
                                                 final Executor executor,
                                                 final FeedService feedService,
                                                 final ThreadLocalBuffer proxyAggregationThreadLocalBuffer,
                                                 final TaskContext taskContext,
                                                 final boolean aggregate) {
        super(executor, taskContext);
        this.taskContext = taskContext;
        this.streamStore = streamStore;
        this.metaDataStatistic = metaDataStatistic;
        this.feedService = feedService;
        this.proxyAggregationThreadLocalBuffer = proxyAggregationThreadLocalBuffer;
        this.aggregate = aggregate;
    }

    @Override
    public void processFeedFiles(final StroomZipRepository stroomZipRepository,
                                 final String feedName,
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
            if (taskContext.isTerminated()) {
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
}
