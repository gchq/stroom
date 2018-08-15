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
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.pipeline.EncodingSelection;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefDataStoreProvider;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.security.Security;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamStore;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorService;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

/**
 * Processes reference data that meets some supplied criteria (feed names,
 * effective from and to dates). The process puts the reference data into key,
 * value maps that can be used later on by the FunctionFilter to perform
 * substitutions when processing events data.
 */
@TaskHandlerBean(task = ReferenceDataLoadTask.class)
class ReferenceDataLoadTaskHandler extends AbstractTaskHandler<ReferenceDataLoadTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataLoadTaskHandler.class);

    private final StreamStore streamStore;
    private final StreamProcessorService streamProcessorService;
    private final PipelineFactory pipelineFactory;
    private final FeedService feedService;
    private final PipelineStore pipelineStore;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final StreamHolder streamHolder;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final RefDataStore refDataStore;
    private final LocationFactoryProxy locationFactory;
    private final StreamCloser streamCloser;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineDataCache pipelineDataCache;
    private final Security security;

    private ErrorReceiverIdDecorator errorReceiver;

    @Inject
    ReferenceDataLoadTaskHandler(final StreamStore streamStore,
                                 final StreamProcessorService streamProcessorService,
                                 final PipelineFactory pipelineFactory,
                                 @Named("cachedFeedService") final FeedService feedService,
                                 @Named("cachedPipelineStore") final PipelineStore pipelineStore,
                                 final PipelineHolder pipelineHolder,
                                 final FeedHolder feedHolder,
                                 final MetaDataHolder metaDataHolder,
                                 final StreamHolder streamHolder,
                                 final RefDataLoaderHolder refDataLoaderHolder,
                                 final RefDataStoreProvider refDataStoreProvider,
                                 final LocationFactoryProxy locationFactory,
                                 final StreamCloser streamCloser,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final PipelineDataCache pipelineDataCache,
                                 final Security security) {
        this.streamStore = streamStore;
        this.streamProcessorService = streamProcessorService;
        this.pipelineFactory = pipelineFactory;
        this.feedService = feedService;
        this.pipelineStore = pipelineStore;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.refDataStore = refDataStoreProvider.getOffHeapStore();
        this.metaDataHolder = metaDataHolder;
        this.locationFactory = locationFactory;
        this.streamHolder = streamHolder;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.streamCloser = streamCloser;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineDataCache = pipelineDataCache;
        this.security = security;
    }

    /**
     * Loads reference data that meets the supplied criteria into the current
     * reference data key, value maps.
     */
    @Override
    public VoidResult exec(final ReferenceDataLoadTask task) {
        return security.secureResult(() -> {
//            final List<RefStreamDefinition> loadedRefStreamDefinitions = new ArrayList<>();
            final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
            errorReceiver = new ErrorReceiverIdDecorator(getClass().getSimpleName(), storedErrorReceiver);
            errorReceiverProxy.setErrorReceiver(errorReceiver);

            try {
                final RefStreamDefinition refStreamDefinition = task.getRefStreamDefinition();

                LOGGER.debug("Loading reference data: {}", refStreamDefinition);

                // Open the stream source.
                final StreamSource streamSource = streamStore.openStreamSource(refStreamDefinition.getStreamId());
                if (streamSource != null) {
                    final Stream stream = streamSource.getStream();
                    try {
                        // Load the feed.
                        final Feed feed = feedService.load(stream.getFeed());
                        feedHolder.setFeed(feed);

                        // Setup the meta data holder.
                        metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, streamProcessorService, pipelineStore));

                        // Set the pipeline so it can be used by a filter if needed.
                        final PipelineDoc pipelineDoc = pipelineStore
                                .readDocument(refStreamDefinition.getPipelineDocRef());
                        pipelineHolder.setPipeline(refStreamDefinition.getPipelineDocRef());

                        // Create the parser.
                        final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                        final Pipeline pipeline = pipelineFactory.create(pipelineData);

                        populateMaps(
                                pipeline,
                                stream,
                                streamSource,
                                feed,
                                stream.getStreamType(),
                                task.getRefStreamDefinition());

                        LOGGER.debug("Finished loading reference data: {}", refStreamDefinition);
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
            return VoidResult.INSTANCE;
        });
    }

    private void populateMaps(final Pipeline pipeline,
                              final Stream stream,
                              final StreamSource streamSource,
                              final Feed feed,
                              final StreamType streamType,
                              final RefStreamDefinition refStreamDefinition) {
//        final List<RefStreamDefinition> loadedRefStreamDefinitions = new ArrayList<>();
        try {
            // Get the stream providers.
            streamHolder.setStream(stream);
            streamHolder.addProvider(streamSource);
            streamHolder.addProvider(streamSource.getChildStream(StreamType.META));
            streamHolder.addProvider(streamSource.getChildStream(StreamType.CONTEXT));

            // Get the main stream provider.
            final StreamSourceInputStreamProvider mainProvider = streamHolder.getProvider(streamSource.getType());

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

                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, stream.getEffectiveMs(), refDataLoader -> {
                    // we are now blocking any other thread loading the same refStreamDefinition

                    try {
                        // Loop over the stream boundaries and process each sequentially.
                        // Typically ref data will only have a single streamNo so if there are
                        // multiple then overrideExisting may be needed.
                        final long streamCount = mainProvider.getStreamCount();
                        for (long streamNo = 0; streamNo < streamCount && !Thread.currentThread().isInterrupted(); streamNo++) {
                            streamHolder.setStreamNo(streamNo);
                            streamLocationFactory.setStreamNo(streamNo + 1);

                            // Get the stream.
                            final StreamSourceInputStream inputStream = mainProvider.getStream(streamNo);

                            // set this loader in the holder so it is available to the pipeline filters
                            refDataLoaderHolder.setRefDataLoader(refDataLoader);
                            // Process the boundary.
                            try {
                                //process the pipeline, ref data will be loaded via the ReferenceDataFilter
                                pipeline.process(inputStream, encoding);
                            } catch (final RuntimeException e) {
                                log(Severity.FATAL_ERROR, e.getMessage(), e);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                // clear the reference to the loader now we have finished with it
                refDataLoaderHolder.setRefDataLoader(null);

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
