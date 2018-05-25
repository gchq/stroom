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

package stroom.refdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.pipeline.EncodingSelection;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineService;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.v2.DocRef;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.security.Security;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamStore;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskContext;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Objects;

/**
 * Processes reference data that meets some supplied criteria (feed names,
 * effective from and to dates). The process puts the reference data into key,
 * value maps that can be used later on by the FunctionFilter to perform
 * substitutions when processing events data.
 */
@TaskHandlerBean(task = ReferenceDataLoadTask.class)
class ReferenceDataLoadTaskHandler extends AbstractTaskHandler<ReferenceDataLoadTask, MapStore> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataLoadTaskHandler.class);

    private final StreamStore streamStore;
    private final PipelineFactory pipelineFactory;
    private final MapStoreHolder mapStoreHolder;
    private final FeedService feedService;
    private final PipelineService pipelineService;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final StreamHolder streamHolder;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final RefDataStore refDataStore;
    private final LocationFactoryProxy locationFactory;
    private final StreamCloser streamCloser;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final TaskContext taskContext;
    private final PipelineDataCache pipelineDataCache;
    private final Security security;

    private ErrorReceiverIdDecorator errorReceiver;

    @Inject
    ReferenceDataLoadTaskHandler(final StreamStore streamStore,
                                 final PipelineFactory pipelineFactory,
                                 final MapStoreHolder mapStoreHolder,
                                 @Named("cachedFeedService") final FeedService feedService,
                                 @Named("cachedPipelineService") final PipelineService pipelineService,
                                 final PipelineHolder pipelineHolder,
                                 final FeedHolder feedHolder,
                                 final StreamHolder streamHolder,
                                 final RefDataLoaderHolder refDataLoaderHolder,
                                 final RefDataStore refDataStore,
                                 final LocationFactoryProxy locationFactory,
                                 final StreamCloser streamCloser,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final TaskContext taskContext,
                                 final PipelineDataCache pipelineDataCache,
                                 final Security security) {
        this.streamStore = streamStore;
        this.pipelineFactory = pipelineFactory;
        this.mapStoreHolder = mapStoreHolder;
        this.feedService = feedService;
        this.pipelineService = pipelineService;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.refDataStore = refDataStore;
        this.locationFactory = locationFactory;
        this.streamHolder = streamHolder;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.streamCloser = streamCloser;
        this.errorReceiverProxy = errorReceiverProxy;
        this.taskContext = taskContext;
        this.pipelineDataCache = pipelineDataCache;
        this.security = security;
    }

    /**
     * Loads reference data that meets the supplied criteria into the current
     * reference data key, value maps.
     */
    @Override
    public MapStore exec(final ReferenceDataLoadTask task) {
        return security.secureResult(() -> {
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
                        final PipelineEntity pipelineEntity = pipelineService
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
            } catch (final RuntimeException e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            }

            return mapStoreBuilder.getMapStore();
        });
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
            } catch (final RuntimeException e) {
                // An exception during start processing is definitely a failure.
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            }

            try {
                // Get the appropriate encoding for the stream type.
                final String encoding = EncodingSelection.select(feed, streamType);

                final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
                locationFactory.setLocationFactory(streamLocationFactory);

                final PipelineEntity pipelineEntity = Objects.requireNonNull(pipelineHolder.getPipeline());
                final DocRef pipelineDocRef = DocRefUtil.create(pipelineEntity);


                // Loop over the stream boundaries and process each
                // sequentially.
                final long streamCount = mainProvider.getStreamCount();
                for (long streamNo = 0; streamNo < streamCount && !taskContext.isTerminated(); streamNo++) {
                    streamHolder.setStreamNo(streamNo);
                    streamLocationFactory.setStreamNo(streamNo + 1);

                    // Get the stream.
                    final StreamSourceInputStream inputStream = mainProvider.getStream(streamNo);

                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            pipelineDocRef,
                            pipelineEntity.getVersion(),
                            streamHolder.getStream().getId(),
                            streamNo);

                    try (RefDataLoader refDataLoader = refDataStore.loader(refStreamDefinition, stream.getEffectiveMs())) {
                        refDataLoaderHolder.setRefDataLoader(refDataLoader);
                        // Process the boundary.
                        try {
                            //process the pipeline, ref data will be loaded via the ReferenceDataFilter
                            pipeline.process(inputStream, encoding);
                        } catch (final RuntimeException e) {
                            log(Severity.FATAL_ERROR, e.getMessage(), e);
                        }
                    } catch (Exception e) {
                        log(Severity.FATAL_ERROR, "Error closing refDataLoader: " + e.getMessage(), e);
                    }
                    // clear the reference to the loader now we have finished with it
                    refDataLoaderHolder.setRefDataLoader(null);
                }


            } catch (final RuntimeException e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            } finally {
                try {
                    pipeline.endProcessing();
                } catch (final RuntimeException e) {
                    log(Severity.FATAL_ERROR, e.getMessage(), e);
                }
            }

        } catch (final IOException | RuntimeException e) {
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
