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

package stroom.pipeline.refdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

/**
 * Processes reference data that meets some supplied criteria (feed names,
 * effective from and to dates). The process puts the reference data into key,
 * value maps that can be used later on by the FunctionFilter to perform
 * substitutions when processing events data.
 */

class ReferenceDataLoadTaskHandler extends AbstractTaskHandler<ReferenceDataLoadTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataLoadTaskHandler.class);

    private final Store streamStore;
    private final PipelineFactory pipelineFactory;
    private final PipelineStore pipelineStore;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final FeedProperties feedProperties;
    private final MetaDataHolder metaDataHolder;
    private final MetaHolder metaHolder;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final RefDataStore refDataStore;
    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineDataCache pipelineDataCache;
    private final SecurityContext securityContext;

    private ErrorReceiverIdDecorator errorReceiver;

    @Inject
    ReferenceDataLoadTaskHandler(final Store streamStore,
                                 final PipelineFactory pipelineFactory,
                                 final PipelineStore pipelineStore,
                                 final PipelineHolder pipelineHolder,
                                 final FeedHolder feedHolder,
                                 final FeedProperties feedProperties,
                                 final MetaDataHolder metaDataHolder,
                                 final MetaHolder metaHolder,
                                 final RefDataLoaderHolder refDataLoaderHolder,
                                 final RefDataStoreFactory refDataStoreFactory,
                                 final LocationFactoryProxy locationFactory,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final PipelineDataCache pipelineDataCache,
                                 final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.pipelineFactory = pipelineFactory;
        this.pipelineStore = pipelineStore;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.feedProperties = feedProperties;
        this.refDataStore = refDataStoreFactory.getOffHeapStore();
        this.metaDataHolder = metaDataHolder;
        this.locationFactory = locationFactory;
        this.metaHolder = metaHolder;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineDataCache = pipelineDataCache;
        this.securityContext = securityContext;
    }

    /**
     * Loads reference data that meets the supplied criteria into the current
     * reference data key, value maps.
     */
    @Override
    public VoidResult exec(final ReferenceDataLoadTask task) {
        return securityContext.secureResult(() -> {
//            final List<RefStreamDefinition> loadedRefStreamDefinitions = new ArrayList<>();
            final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
            errorReceiver = new ErrorReceiverIdDecorator(getClass().getSimpleName(), storedErrorReceiver);
            errorReceiverProxy.setErrorReceiver(errorReceiver);

            final RefStreamDefinition refStreamDefinition = task.getRefStreamDefinition();
            LOGGER.debug("Loading reference data: {}", refStreamDefinition);

            // Open the stream source.
            try (final Source source = streamStore.openSource(refStreamDefinition.getStreamId())) {
                if (source != null) {
                    final Meta meta = source.getMeta();

                    // Load the feed.
                    final String feedName = meta.getFeedName();
                    feedHolder.setFeedName(feedName);

                    // Setup the meta data holder.
                    metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

                    // Set the pipeline so it can be used by a filter if needed.
                    final PipelineDoc pipelineDoc = pipelineStore
                            .readDocument(refStreamDefinition.getPipelineDocRef());
                    pipelineHolder.setPipeline(refStreamDefinition.getPipelineDocRef());

                    // Create the parser.
                    final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                    final Pipeline pipeline = pipelineFactory.create(pipelineData);

                    populateMaps(
                            pipeline,
                            meta,
                            source,
                            feedName,
                            meta.getTypeName(),
                            task.getRefStreamDefinition());

                    LOGGER.debug("Finished loading reference data: {}", refStreamDefinition);
                }
            } catch (final IOException | RuntimeException e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
            }
            return VoidResult.INSTANCE;
        });
    }

    private void populateMaps(final Pipeline pipeline,
                              final Meta meta,
                              final Source source,
                              final String feedName,
                              final String streamTypeName,
                              final RefStreamDefinition refStreamDefinition) {
        // Set the source meta.
        metaHolder.setMeta(meta);

        // Start processing.
        try {
            pipeline.startProcessing();
        } catch (final RuntimeException e) {
            // An exception during start processing is definitely a failure.
            log(Severity.FATAL_ERROR, e.getMessage(), e);
        }

        try {
            // Get the appropriate encoding for the stream type.
            final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

            final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
            locationFactory.setLocationFactory(streamLocationFactory);

            refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, meta.getEffectiveMs(), refDataLoader -> {
                // we are now blocking any other thread loading the same refStreamDefinition

                try {
                    // Loop over the stream boundaries and process each sequentially.
                    // Typically ref data will only have a single streamNo so if there are
                    // multiple then overrideExisting may be needed.
                    final long count = source.count();
                    for (long index = 0; index < count && !Thread.currentThread().isInterrupted(); index++) {
                        metaHolder.setStreamNo(index);
                        streamLocationFactory.setStreamNo(index + 1);

                        try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                            // Get the stream providers.
                            metaHolder.setInputStreamProvider(inputStreamProvider);

                            // Get the stream.
                            final InputStream inputStream = inputStreamProvider.get();

                            // set this loader in the holder so it is available to the pipeline filters
                            refDataLoaderHolder.setRefDataLoader(refDataLoader);
                            // Process the boundary.
                            try {
                                //process the pipeline, ref data will be loaded via the ReferenceDataFilter
                                pipeline.process(inputStream, encoding);
                            } catch (final RuntimeException e) {
                                log(Severity.FATAL_ERROR, e.getMessage(), e);
                            }
                        } catch (final IOException | RuntimeException e) {
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
