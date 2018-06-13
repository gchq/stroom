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
import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.pipeline.EncodingSelection;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.security.Security;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = ContextDataLoadTask.class)
class ContextDataLoadTaskHandler extends AbstractTaskHandler<ContextDataLoadTask, List<RefStreamDefinition>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextDataLoadTaskHandler.class);

    private final PipelineFactory pipelineFactory;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final RefDataStore refDataStore;
    private final FeedHolder feedHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineService pipelineService;
    private final PipelineDataCache pipelineDataCache;
    private final Security security;

    private ErrorReceiverIdDecorator errorReceiver;

    @Inject
    ContextDataLoadTaskHandler(final PipelineFactory pipelineFactory,
                               final RefDataLoaderHolder refDataLoaderHolder,
                               final RefDataStore refDataStore,
                               final FeedHolder feedHolder,
                               final ErrorReceiverProxy errorReceiverProxy,
                               @Named("cachedPipelineService")
                               final PipelineService pipelineService,
                               final PipelineDataCache pipelineDataCache,
                               final Security security) {
        this.pipelineFactory = pipelineFactory;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.refDataStore = refDataStore;
        this.feedHolder = feedHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineService = pipelineService;
        this.pipelineDataCache = pipelineDataCache;
        this.security = security;
    }

    @Override
    public List<RefStreamDefinition> exec(final ContextDataLoadTask task) {
        return security.secureResult(() -> {
            final List<RefStreamDefinition> loadedRefStreamDefinitions = new ArrayList<>();
            final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
//            final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(storedErrorReceiver);
            errorReceiver = new ErrorReceiverIdDecorator(getClass().getSimpleName(), storedErrorReceiver);
            errorReceiverProxy.setErrorReceiver(errorReceiver);

            final InputStream inputStream = task.getInputStream();
            final Stream stream = task.getStream();
            final Feed feed = task.getFeed();

            if (inputStream != null) {
                final StreamCloser streamCloser = new StreamCloser();
                streamCloser.add(inputStream);

                try {
                    String contextIdentifier = null;

                    if (LOGGER.isDebugEnabled()) {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("(feed = ");
                        sb.append(feed.getName());
                        if (stream != null) {
                            sb.append(", source id = ");
                            sb.append(stream.getId());
                        }
                        sb.append(")");
                        contextIdentifier = sb.toString();
                        LOGGER.debug("Loading context data " + contextIdentifier);
                    }

                    // Create the parser.
                    final PipelineEntity pipelineEntity = pipelineService.loadByUuid(task.getContextPipeline().getUuid());
                    final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
                    final Pipeline pipeline = pipelineFactory.create(pipelineData);

                    feedHolder.setFeed(feed);

                    // Get the appropriate encoding for the stream type.
                    final String encoding = EncodingSelection.select(feed, StreamType.CONTEXT);
//                    mapStoreHolder.setMapStoreBuilder(mapStoreBuilder);

                    // TODO is it always 0 for context streams?
                    int streamNo = 0;
                    RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            pipelineEntity.getUuid(),
                            pipelineEntity.getVersion(),
                            stream.getId(),
                            streamNo);

                    refDataStore.doWithLoader(refStreamDefinition, stream.getEffectiveMs(), refDataLoader -> {
                        refDataLoaderHolder.setRefDataLoader(refDataLoader);
                        // Process the boundary.
                        try {
                            // Parse the stream. The ReferenceDataFilter will process the context data
                            pipeline.process(inputStream, encoding);

                            loadedRefStreamDefinitions.add(refStreamDefinition);
                        } catch (final RuntimeException e) {
                            log(Severity.FATAL_ERROR, e.getMessage(), e);
                        }
                    });

                    // clear the reference to the loader now we have finished with it
                    refDataLoaderHolder.setRefDataLoader(null);

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Finished loading context data " + contextIdentifier);
                    }
                } catch (final RuntimeException e) {
                    log(Severity.FATAL_ERROR, "Error loading context data: " + e.getMessage(), e);
                } finally {
                    try {
                        // Close all open streams.
                        streamCloser.close();
                    } catch (final IOException e) {
                        log(Severity.FATAL_ERROR, "Error closing context data stream: " + e.getMessage(), e);
                    }
                }
            }
            return loadedRefStreamDefinitions;
        });
    }

    private void log(final Severity severity, final String message, final Throwable e) {
        LOGGER.debug(message, e);
        errorReceiver.log(severity, null, getClass().getSimpleName(), message, e);
    }
}
