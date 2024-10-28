/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.headless;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.Meta;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;


class CliTranslationTaskHandler {

    private final PipelineFactory pipelineFactory;
    private final FeedProperties feedProperties;
    private final PipelineStore pipelineStore;
    private final MetaData metaData;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final RecordErrorReceiver recordErrorReceiver;
    private final PipelineDataCache pipelineDataCache;
    private final MetaHolder metaHolder;
    private final SecurityContext securityContext;

    @Inject
    CliTranslationTaskHandler(final PipelineFactory pipelineFactory,
                              final FeedProperties feedProperties,
                              final PipelineStore pipelineStore,
                              final MetaData metaData,
                              final PipelineHolder pipelineHolder,
                              final FeedHolder feedHolder,
                              final MetaDataHolder metaDataHolder,
                              final ErrorReceiverProxy errorReceiverProxy,
                              final ErrorWriterProxy errorWriterProxy,
                              final RecordErrorReceiver recordErrorReceiver,
                              final PipelineDataCache pipelineDataCache,
                              final MetaHolder metaHolder,
                              final SecurityContext securityContext) {
        this.pipelineFactory = pipelineFactory;
        this.feedProperties = feedProperties;
        this.pipelineStore = pipelineStore;
        this.metaData = metaData;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.recordErrorReceiver = recordErrorReceiver;
        this.pipelineDataCache = pipelineDataCache;
        this.metaHolder = metaHolder;
        this.securityContext = securityContext;
    }

    public void exec(final InputStream dataStream,
                     final InputStream metaStream,
                     final InputStream contextStream,
                     final Writer errorWriter,
                     final TaskContext taskContext) {
        securityContext.secure(() -> {
            // Elevate user permissions so that inherited pipelines that the user only has 'Use' permission on
            // can be read.
            securityContext.useAsRead(() -> {
                try {
                    // Setup the error handler and receiver.
                    errorWriterProxy.setErrorWriter(new CliErrorWriter(errorWriter));
                    errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

                    if (metaStream == null) {
                        throw new RuntimeException("No meta data found");
                    }

                    // Load the meta and context data.
                    final AttributeMap metaData = new AttributeMap();
                    AttributeMapUtil.read(metaStream, metaData);

                    // Get the feed.
                    final String feedName = metaData.get(StandardHeaderArguments.FEED);
                    feedHolder.setFeedName(feedName);

                    // Setup the meta data holder.
                    metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

                    // Set the pipeline so it can be used by a filter if needed.
                    final List<DocRef> pipelines = pipelineStore.findByName(feedName);
                    if (pipelines == null || pipelines.isEmpty()) {
                        throw ProcessException.create("No pipeline found for feed name '" + feedName + "'");
                    }
                    if (pipelines.size() > 1) {
                        throw ProcessException.create("More than one pipeline found for feed name '" + feedName + "'");
                    }

                    final DocRef pipelineRef = pipelines.get(0);
                    pipelineHolder.setPipeline(pipelineRef);

                    // Create the parser.
                    final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                    final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                    final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);

                    // Output the meta data for the new stream.
                    this.metaData.putAll(metaData);

                    // Set effective time.
                    Long effectiveMs = null;
                    try {
                        final String effectiveTime = metaData.get(StandardHeaderArguments.EFFECTIVE_TIME);
                        if (effectiveTime != null && !effectiveTime.isEmpty()) {
                            effectiveMs = DateUtil.parseNormalDateTimeString(effectiveTime);
                        }
                    } catch (final RuntimeException e) {
                        outputError(e);
                    }

                    // Create the stream.
                    final Meta meta = Meta.builder()
                            .effectiveMs(effectiveMs)
                            .feedName(feedName)
                            .build();

                    // Add stream providers for lookups etc.
                    final BasicInputStreamProvider inputStreamProvider = new BasicInputStreamProvider();
                    inputStreamProvider.put(null, new IgnoreCloseInputStream(dataStream), dataStream.available());
                    inputStreamProvider.put(StreamTypeNames.RAW_EVENTS,
                            new IgnoreCloseInputStream(dataStream),
                            dataStream.available());
                    if (metaStream != null) {
                        inputStreamProvider.put(StreamTypeNames.META,
                                new IgnoreCloseInputStream(metaStream),
                                metaStream.available());
                    }
                    if (contextStream != null) {
                        inputStreamProvider.put(StreamTypeNames.CONTEXT,
                                new IgnoreCloseInputStream(contextStream),
                                contextStream.available());
                    }

                    metaHolder.setMeta(meta);
                    metaHolder.setInputStreamProvider(inputStreamProvider);

                    try {
                        pipeline.process(dataStream, feedProperties.getEncoding(
                                feedName, feedProperties.getStreamTypeName(feedName), null));
                    } catch (final RuntimeException e) {
                        outputError(e);
                    }
                } catch (final IOException | RuntimeException e) {
                    outputError(e);
                }
            });
        });
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Throwable ex) {
        if (errorReceiverProxy != null && !(ex instanceof LoggedException)) {
            try {
                if (ex.getMessage() != null) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, "PipelineStreamProcessor", ex.getMessage(), ex);
                } else {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, "PipelineStreamProcessor", ex.toString(), ex);
                }
            } catch (final RuntimeException e) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }
        }
    }
}
