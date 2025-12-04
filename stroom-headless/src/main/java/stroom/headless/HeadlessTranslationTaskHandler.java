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
import stroom.pipeline.factory.HasTargets;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.filter.RecordOutputFilter;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.filter.XsltFilter;
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


class HeadlessTranslationTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HeadlessTranslationTaskHandler.class);

    private static final ElementId ELEMENT_ID = new ElementId("PipelineStreamProcessor");

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
    HeadlessTranslationTaskHandler(final PipelineFactory pipelineFactory,
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
                     final HeadlessFilter headlessFilter,
                     final TaskContext taskContext) {
        securityContext.secure(() -> {
            // Elevate user permissions so that inherited pipelines that the user only has 'Use'
            // permission on can be read.
            securityContext.useAsRead(() -> {
                try {
                    // Setup the error handler and receiver.
                    errorWriterProxy.setErrorWriter(headlessFilter);
                    errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

                    if (metaStream == null) {
                        throw new RuntimeException("No meta data found");
                    }

                    // Load the meta and context data.
                    final AttributeMap metaData = new AttributeMap();
                    AttributeMapUtil.read(metaStream, metaData);

                    // Get the feed.
                    final String feedName = metaData.get(StandardHeaderArguments.FEED);
                    if (NullSafe.isBlankString(feedName)) {
                        throw ProcessException.create("The Feed attribute is not set in the meta data.");
                    }
                    feedHolder.setFeedName(feedName);

                    // Setup the meta data holder.
                    metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

                    // Set the pipeline, so it can be used by a filter if needed.
                    final List<DocRef> pipelines = pipelineStore.findByName(feedName);
                    final int pipelinesCount = NullSafe.size(pipelines);
                    if (pipelinesCount == 0) {
                        throw ProcessException.create("No pipeline found matching feed name '" + feedName + "'");
                    } else if (pipelinesCount > 1) {
                        throw ProcessException.create(
                                "More than one pipeline found matching feed name '" + feedName + "'");
                    }

                    final DocRef pipelineRef = pipelines.getFirst();
                    pipelineHolder.setPipeline(pipelineRef);

                    LOGGER.info("Processing Feed '{}' using pipeline '{}' ({})",
                            feedName, pipelineRef.getName(), pipelineRef.getUuid());

                    // Create the parser.
                    final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                    final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                    final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);

                    // Find last XSLT filter.
                    final XMLFilter lastFilter = getLastFilter(pipeline);
                    if (!(lastFilter instanceof final HasTargets hasTargets)) {
                        throw ProcessException.create(
                                "No appendable filters can be found in pipeline '" + pipelineRef.getName() + "'");
                    }
                    hasTargets.setTarget(headlessFilter);

                    // Output the meta-data for the new stream.
                    this.metaData.putAll(metaData);
                    headlessFilter.changeMetaData(metaData);

                    // Set effective time.
                    Long effectiveMs = null;
                    try {
                        final String effectiveTime = metaData.get(StandardHeaderArguments.EFFECTIVE_TIME);
                        if (NullSafe.isNonEmptyString(effectiveTime)) {
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
                    inputStreamProvider.put(
                            null,
                            new IgnoreCloseInputStream(dataStream),
                            dataStream.available());
                    inputStreamProvider.put(
                            StreamTypeNames.RAW_EVENTS,
                            new IgnoreCloseInputStream(dataStream),
                            dataStream.available());
                    inputStreamProvider.put(
                            StreamTypeNames.META,
                            new IgnoreCloseInputStream(metaStream),
                            metaStream.available());
                    if (contextStream != null) {
                        inputStreamProvider.put(
                                StreamTypeNames.CONTEXT,
                                new IgnoreCloseInputStream(contextStream),
                                contextStream.available());
                    }

                    metaHolder.setMeta(meta);
                    metaHolder.setInputStreamProvider(inputStreamProvider);

                    try {
                        // Processing the data stream so use null child type
                        pipeline.process(
                                dataStream,
                                feedProperties.getEncoding(
                                        feedName,
                                        feedProperties.getStreamTypeName(feedName),
                                        null));
                    } catch (final RuntimeException e) {
                        outputError(e);
                    }
                } catch (final IOException | RuntimeException e) {
                    outputError(e);
                }
            });
        });
    }

    private XMLFilter getLastFilter(final Pipeline pipeline) {
        XMLFilter filter = getLastFilter(pipeline, RecordOutputFilter.class);
        if (filter == null) {
            filter = getLastFilter(pipeline, SchemaFilter.class);
        }
        if (filter == null) {
            filter = getLastFilter(pipeline, XsltFilter.class);
        }
        return filter;
    }

    private <T extends XMLFilter> T getLastFilter(final Pipeline pipeline, final Class<T> clazz) {
        return NullSafe.last(pipeline.findFilters(clazz));
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Throwable ex) {
        if (errorReceiverProxy != null && !(ex instanceof LoggedException)) {
            try {
                if (ex.getMessage() != null) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, ELEMENT_ID, ex.getMessage(), ex);
                } else {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, ELEMENT_ID, ex.toString(), ex);
                }
            } catch (final RuntimeException e) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof final ErrorStatistics errorStatistics) {
                errorStatistics.checkRecord(-1);
            }
        }
    }
}
