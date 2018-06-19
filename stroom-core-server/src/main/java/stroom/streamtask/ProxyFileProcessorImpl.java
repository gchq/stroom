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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.FeedDocCache;
import stroom.feed.AttributeMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.FeedDoc;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.properties.api.StroomPropertyService;
import stroom.proxy.repo.ProxyFileHandler;
import stroom.proxy.repo.ProxyFileProcessor;
import stroom.proxy.repo.StroomZipRepository;
import stroom.data.store.api.StreamStore;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class that reads a nested directory tree of stroom zip files.
 * <p>
 * <p>
 * TODO - This class is extended in ProxyAggregationExecutor in Stroom
 * so changes to the way files are stored in the zip repository
 * may have an impact on Stroom while it is using stroom.util.zip as opposed
 * to stroom-proxy-zip.  Need to pull all the zip repository stuff out
 * into its own repo with its own lifecycle and a clearly defined API,
 * then both stroom-proxy and stroom can use it.
 */
final class ProxyFileProcessorImpl implements ProxyFileProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFileProcessorImpl.class);

    private final ProxyFileHandler feedFileProcessorHelper = new ProxyFileHandler();

    private final static int DEFAULT_MAX_AGGREGATION = 10000;
    final static long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private final StreamStore streamStore;
    private final FeedDocCache feedDocCache;
    private final MetaDataStatistic metaDataStatistic;
    private final int maxAggregation;
    private final long maxStreamSize;
    private final boolean aggregate = true;
    private final ProxyFileHandler proxyFileHandler = new ProxyFileHandler();
    private volatile boolean stop = false;

    @Inject
    ProxyFileProcessorImpl(final StreamStore streamStore,
                           final FeedDocCache feedDocCache,
                           final MetaDataStatistic metaDataStatistic,
                           final StroomPropertyService propertyService) {
        this(
                streamStore,
                feedDocCache,
                metaDataStatistic,
                propertyService.getIntProperty("stroom.maxAggregation", DEFAULT_MAX_AGGREGATION),
                getByteSize(propertyService.getProperty("stroom.maxStreamSize"), DEFAULT_MAX_STREAM_SIZE)
        );
    }

    ProxyFileProcessorImpl(final StreamStore streamStore,
                           final FeedDocCache feedDocCache,
                           final MetaDataStatistic metaDataStatistic,
                           final int maxAggregation,
                           final long maxStreamSize) {
        this.streamStore = streamStore;
        this.feedDocCache = feedDocCache;
        this.metaDataStatistic = metaDataStatistic;
        this.maxAggregation = maxAggregation;
        this.maxStreamSize = maxStreamSize;
    }

    @Override
    public void processFeedFiles(final StroomZipRepository stroomZipRepository, final String feedName, final List<Path> fileList) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("processFeedFiles() - Started {} ({} Files)", feedName, fileList.size());

        if (!optional.isPresent()) {
            LOGGER.error("processFeedFiles() - " + feedName + " Failed to find feed");
            return;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("processFeedFiles() - " + feedName + " " + fileList);
        }

        // We don't want to aggregate reference feeds.
        final FeedDoc feed = optional.get();
        final boolean oneByOne = feed.isReference() || !aggregate;

        List<StreamTargetStroomStreamHandler> handlers = openStreamHandlers(feed);
        List<Path> deleteFileList = new ArrayList<>();

        long sequence = 1;
        long maxAggregation = this.maxAggregation;
        if (oneByOne) {
            maxAggregation = 1;
        }

        Long nextBatchBreak = this.maxStreamSize;

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
                    proxyFileHandler.deleteFiles(stroomZipRepository, deleteFileList);

                    // Start new batch
                    deleteFileList = new ArrayList<>();
                    handlers = openStreamHandlers(feed);
                    sequence = 1;
                }
                sequence = feedFileProcessorHelper.processFeedFile(handlers, stroomZipRepository, file, streamProgressMonitor, sequence);
                deleteFileList.add(file);

            } catch (final IOException | RuntimeException e) {
                handlers = closeDeleteStreamHandlers(handlers);
            }
        }
        closeStreamHandlers(handlers);
        proxyFileHandler.deleteFiles(stroomZipRepository, deleteFileList);
        LOGGER.info("processFeedFiles() - Completed {} in {}", feedName, logExecutionTime);
    }

    private List<StreamTargetStroomStreamHandler> openStreamHandlers(final FeedDoc feed) {
        // We don't want to aggregate reference feeds.
        final boolean oneByOne = feed.isReference() || !aggregate;

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedDocCache, metaDataStatistic, feed.getName(), feed.getStreamType());

        streamTargetStroomStreamHandler.setOneByOne(oneByOne);

        final AttributeMap globalAttributeMap = new AttributeMap();
        globalAttributeMap.put(StroomHeaderArguments.FEED, feed.getName());

//        try {
            streamTargetStroomStreamHandler.handleHeader(globalAttributeMap);
//        } catch (final IOException ioEx) {
//            streamTargetStroomStreamHandler.close();
//            throw new RuntimeException(ioEx);
//        }

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

    static long getByteSize(final String propertyValue, final long defaultValue) {
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

    /**
     * Stops the task as soon as possible.
     */
    public void stop() {
        stop = true;
    }
}
