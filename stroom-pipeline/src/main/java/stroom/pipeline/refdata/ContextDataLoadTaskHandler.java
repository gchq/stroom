/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.refdata;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.BasicStreamCloser;
import stroom.util.io.StreamCloser;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


class ContextDataLoadTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextDataLoadTaskHandler.class);

    private final PipelineFactory pipelineFactory;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final FeedHolder feedHolder;
    private final FeedProperties feedProperties;
    private final MetaDataHolder metaDataHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineStore pipelineStore;
    private final MetaHolder metaHolder;
    private final PipelineDataCache pipelineDataCache;
    private final SecurityContext securityContext;

    private ErrorReceiverIdDecorator errorReceiver;

    @Inject
    ContextDataLoadTaskHandler(final PipelineFactory pipelineFactory,
                               final RefDataLoaderHolder refDataLoaderHolder,
                               final FeedHolder feedHolder,
                               final FeedProperties feedProperties,
                               final MetaDataHolder metaDataHolder,
                               final ErrorReceiverProxy errorReceiverProxy,
                               final PipelineStore pipelineStore,
                               final MetaHolder metaHolder,
                               final PipelineDataCache pipelineDataCache,
                               final SecurityContext securityContext) {
        this.pipelineFactory = pipelineFactory;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.feedHolder = feedHolder;
        this.feedProperties = feedProperties;
        this.metaDataHolder = metaDataHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineStore = pipelineStore;
        this.metaHolder = metaHolder;
        this.pipelineDataCache = pipelineDataCache;
        this.securityContext = securityContext;
    }

    public void exec(final InputStream inputStream,
                     final Meta meta,
                     final String feedName,
                     final DocRef contextPipeline,
                     final RefStreamDefinition refStreamDefinition,
                     final RefDataStore refDataStore,
                     final TaskContext taskContext) {
        Objects.requireNonNull(meta);
        securityContext.secure(() -> {
            // Elevate user permissions so that inherited pipelines that the user only has 'Use'
            // permission on can be read.
            securityContext.useAsRead(() -> {
                final StoredErrorReceiver storedErrorReceiver = new StoredErrorReceiver();
                errorReceiver = new ErrorReceiverIdDecorator(
                        new ElementId(getClass().getSimpleName()), storedErrorReceiver);
                errorReceiverProxy.setErrorReceiver(errorReceiver);

                if (inputStream != null) {
                    final StreamCloser streamCloser = new BasicStreamCloser();
                    streamCloser.add(inputStream);

                    try {
                        String contextIdentifier = null;

                        if (LOGGER.isDebugEnabled()) {
                            final StringBuilder sb = new StringBuilder();
                            sb.append("(feed = ");
                            sb.append(feedName);
                            if (meta != null) {
                                sb.append(", source id = ");
                                sb.append(meta.getId());
                            }
                            sb.append(")");
                            contextIdentifier = sb.toString();
                            LOGGER.debug("Loading context data " + contextIdentifier);
                        }

                        // Create the parser.
                        final PipelineDoc pipelineDoc = pipelineStore.readDocument(contextPipeline);
                        final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                        final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);

                        feedHolder.setFeedName(feedName);

                        // Setup the meta data holder.
                        metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

                        // Get the appropriate encoding for the stream type.
                        final String streamTypeName = meta != null
                                ? meta.getTypeName()
                                : null;
                        final String encoding = feedProperties.getEncoding(
                                feedName, streamTypeName, StreamTypeNames.CONTEXT);
//                    mapStoreHolder.setMapStoreBuilder(mapStoreBuilder);

                        // TODO is it always 0 for context streams?
//                    RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
//                            pipelineDoc.getUuid(),
//                            pipelineDoc.getVersion(),
//                            stream.getId());

                        final long effectiveMs = Objects.requireNonNullElseGet(
                                meta.getEffectiveMs(),
                                () -> meta.getCreateMs());

                        refDataStore.doWithLoaderUnlessComplete(
                                refStreamDefinition,
                                effectiveMs,
                                refDataLoader -> {
                                    // set this loader in the holder so it is available to the pipeline filters
                                    refDataLoaderHolder.setRefDataLoader(refDataLoader);
                                    // Process the boundary.
                                    try {
                                        // Parse the stream. The ReferenceDataFilter will process the context data
                                        pipeline.process(inputStream, encoding);

                                        refDataLoader.completeProcessing(ProcessingState.COMPLETE);
                                    } catch (final TaskTerminatedException e) {
                                        // Task terminated
                                        log(Severity.FATAL_ERROR, e.getMessage(), e);
                                        refDataLoader.completeProcessing(ProcessingState.TERMINATED);
                                    } catch (final Exception e) {
                                        log(Severity.FATAL_ERROR, e.getMessage(), e);
                                        refDataLoader.completeProcessing(ProcessingState.FAILED);
                                    } finally {
                                        try {
                                            pipeline.endProcessing();
                                        } catch (final RuntimeException e) {
                                            log(Severity.FATAL_ERROR, e.getMessage(), e);
                                            refDataLoader.completeProcessing(ProcessingState.FAILED);
                                        }
                                    }
                                });

                        // clear the reference to the loader now we have finished with it
                        refDataLoaderHolder.setRefDataLoader(null);

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Finished loading context data " + contextIdentifier);
                        }
                    } catch (final IOException | RuntimeException e) {
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
//            return loadedRefStreamDefinitions;
            });
        });
    }

    private void log(final Severity severity, final String message, final Throwable e) {
        LOGGER.trace(message, e);

        // LoggedException has already been logged
        if (errorReceiverProxy != null && !(e instanceof LoggedException)) {
            String msg = message;
            if (msg == null) {
                msg = e.toString();
            }
            errorReceiver.log(severity, null, new ElementId(getClass().getSimpleName()), msg, e);
        }
    }
}
