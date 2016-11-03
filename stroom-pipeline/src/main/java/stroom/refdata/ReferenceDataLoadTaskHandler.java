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

package stroom.refdata;

import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.io.StreamCloser;
import stroom.pipeline.server.EncodingSelection;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.StreamLocationFactory;
import stroom.pipeline.server.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import org.springframework.context.annotation.Scope;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * Processes reference data that meets some supplied criteria (feed names,
 * effective from and to dates). The process puts the reference data into key,
 * value maps that can be used later on by the FunctionFilter to perform
 * substitutions when processing events data.
 */
@TaskHandlerBean(task = ReferenceDataLoadTask.class)
@Scope(value = StroomScope.TASK)
public class ReferenceDataLoadTaskHandler extends AbstractTaskHandler<ReferenceDataLoadTask, MapStore> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ReferenceDataLoadTaskHandler.class);
    @Resource
    private StreamStore streamStore;
    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private MapStoreHolder mapStoreHolder;
    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "cachedPipelineEntityService")
    private PipelineEntityService pipelineEntityService;
    @Resource
    private PipelineHolder pipelineHolder;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private StreamHolder streamHolder;
    @Resource
    private LocationFactoryProxy locationFactory;
    @Resource
    private StreamCloser streamCloser;
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;
    @Resource
    private TaskMonitor taskMonitor;
    @Resource
    private PipelineDataCache pipelineDataCache;

    private ErrorReceiverIdDecorator errorReceiver;

    /**
     * Loads reference data that meets the supplied criteria into the current
     * reference data key, value maps.
     */
    @Override
    public MapStore exec(final ReferenceDataLoadTask task) {
        final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
        final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(storedErrorReceiver);
        errorReceiver = new ErrorReceiverIdDecorator(getClass().getSimpleName(), storedErrorReceiver);
        errorReceiverProxy.setErrorReceiver(errorReceiver);

        try {
            final MapStoreCacheKey mapStorePoolKey = task.getMapStorePoolKey();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loading reference data: " + mapStorePoolKey.toString());
            }

            // Open the stream source.
            final StreamSource streamSource = streamStore.openStreamSource(mapStorePoolKey.getStreamId());
            if (streamSource != null) {
                final Stream stream = streamSource.getStream();
                try {
                    // Load the feed.
                    final Feed feed = feedService.load(stream.getFeed());
                    feedHolder.setFeed(feed);

                    // Set the pipeline so it can be used by a filter if needed.
                    final PipelineEntity pipelineEntity = pipelineEntityService
                            .loadByUuid(mapStorePoolKey.getPipeline().getUuid());
                    pipelineHolder.setPipeline(pipelineEntity);

                    // Create the parser.
                    final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
                    final Pipeline pipeline = pipelineFactory.create(pipelineData);

                    populateMaps(pipeline, stream, streamSource, feed, stream.getStreamType(), mapStoreBuilder);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Finished loading reference data: " + mapStorePoolKey.toString());
                    }
                } finally {
                    try {
                        // Close all open streams.
                        streamCloser.close();
                    } catch (final IOException e) {
                        log(Severity.FATAL_ERROR, e.getMessage(), e);
                    }

                    streamStore.closeStreamSource(streamSource);
                }
            }
        } catch (final Exception e) {
            log(Severity.FATAL_ERROR, e.getMessage(), e);
        }

        return mapStoreBuilder.getMapStore();
    }

    private void populateMaps(final Pipeline pipeline, final Stream stream, final StreamSource streamSource,
            final Feed feed, final StreamType streamType, final MapStoreBuilder mapStoreBuilder) {
        try {
            // Get the stream providers.
            streamHolder.setStream(stream);
            streamHolder.addProvider(streamSource);
            streamHolder.addProvider(streamSource.getChildStream(StreamType.META));
            streamHolder.addProvider(streamSource.getChildStream(StreamType.CONTEXT));

            // Get the main stream provider.
            final StreamSourceInputStreamProvider mainProvider = streamHolder.getProvider(streamSource.getType());

            // Set the map store.
            mapStoreHolder.setMapStoreBuilder(mapStoreBuilder);

            // Start processing.
            try {
                pipeline.startProcessing();
            } catch (final Exception e) {
                // An exception during start processing is definitely a failure.
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            }

            try {
                // Get the appropriate encoding for the stream type.
                final String encoding = EncodingSelection.select(feed, streamType);

                final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
                locationFactory.setLocationFactory(streamLocationFactory);

                // Loop over the stream boundaries and process each
                // sequentially.
                final long streamCount = mainProvider.getStreamCount();
                for (long streamNo = 0; streamNo < streamCount && !taskMonitor.isTerminated(); streamNo++) {
                    streamHolder.setStreamNo(streamNo);
                    streamLocationFactory.setStreamNo(streamNo + 1);

                    // Get the stream.
                    final StreamSourceInputStream inputStream = mainProvider.getStream(streamNo);

                    // Process the boundary.
                    try {
                        pipeline.process(inputStream, encoding);
                    } catch (final Exception e) {
                        log(Severity.FATAL_ERROR, e.getMessage(), e);
                    }
                }
            } catch (final Exception e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            } finally {
                try {
                    pipeline.endProcessing();
                } catch (final Exception e) {
                    log(Severity.FATAL_ERROR, e.getMessage(), e);
                }
            }

        } catch (final Exception e) {
            log(Severity.FATAL_ERROR, e.getMessage(), e);
        }
    }

    private void log(final Severity severity, final String message, final Throwable e) {
        LOGGER.trace(message, e);

        String msg = message;
        if (msg == null) {
            msg = e.toString();
        }
        errorReceiver.log(severity, null, getClass().getSimpleName(), msg, e);
    }
}
