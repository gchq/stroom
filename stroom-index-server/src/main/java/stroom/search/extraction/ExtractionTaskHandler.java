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

package stroom.search.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.errorhandler.TerminatedException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.filter.IdEnrichmentFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.search.SearchException;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.fs.serializable.RASegmentInputStream;
import stroom.task.TaskContext;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ExtractionTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ExtractionTaskHandler.class);

    private final StreamStore streamStore;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final CurrentUserHolder currentUserHolder;
    private final StreamHolder streamHolder;
    private final PipelineHolder pipelineHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineFactory pipelineFactory;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final TaskContext taskContext;
    private final Security security;
    private final SecurityContext securityContext;

    private ExtractionTask task;

    @Inject
    ExtractionTaskHandler(final StreamStore streamStore,
                          final FeedHolder feedHolder,
                          final MetaDataHolder metaDataHolder,
                          final CurrentUserHolder currentUserHolder,
                          final StreamHolder streamHolder,
                          final PipelineHolder pipelineHolder,
                          final ErrorReceiverProxy errorReceiverProxy,
                          final PipelineFactory pipelineFactory,
                          @Named("cachedPipelineStore") final PipelineStore pipelineStore,
                          final PipelineDataCache pipelineDataCache,
                          final TaskContext taskContext,
                          final Security security,
                          final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.currentUserHolder = currentUserHolder;
        this.streamHolder = streamHolder;
        this.pipelineHolder = pipelineHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineFactory = pipelineFactory;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.taskContext = taskContext;
        this.security = security;
        this.securityContext = securityContext;
    }

    public VoidResult exec(final ExtractionTask task) {
        return security.useAsReadResult(() -> {
            LAMBDA_LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        taskContext.setName("Extraction");
                        if (!Thread.currentThread().isInterrupted()) {
                            final String streamId = String.valueOf(task.getStreamId());
                            taskContext.info("Extracting " + task.getEventIds().length + " records from stream " + streamId);

                            extract(task);
                        }
                    },
                    () -> "ExtractionTaskHandler.exec()");

            return VoidResult.INSTANCE;
        });
    }

    private void extract(final ExtractionTask task) {
        try {
            this.task = task;

            // Set the current user.
            currentUserHolder.setCurrentUser(securityContext.getUserId());

            final DocRef pipelineRef = task.getPipelineRef();

            // Get the translation that will be used to display results.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            if (pipelineDoc == null) {
                throw new SearchException("Unable to find result pipeline: " + pipelineRef);
            }

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactory.create(pipelineData);
            if (pipeline == null) {
                throw new SearchException("Unable to create parser for pipeline: " + pipelineRef);
            }

            // Setup the id enrichment filter to try and recreate the conditions
            // present when the index was built. We need to do this because the
            // input stream is now filtered to only include events matched by
            // the search. This means that the event ids cannot be calculated by
            // just counting events.
            final String streamId = String.valueOf(task.getStreamId());
            final IdEnrichmentFilter idEnrichmentFilter = getFilter(pipeline, IdEnrichmentFilter.class);
            idEnrichmentFilter.setup(streamId, task.getEventIds());

            // Setup the search result output filter to expect the same order of
            // event ids and give it the result cache and stored data to write
            // values to.
            final SearchResultOutputFilter searchResultOutputFilter = getFilter(pipeline,
                    SearchResultOutputFilter.class);

            searchResultOutputFilter.setup(task.getFieldIndexes(), task.getResultReceiver());

            // Process the stream segments.
            processData(task.getStreamId(), task.getEventIds(), pipelineRef, pipeline);

        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
        }
    }

    private <T extends XMLFilter> T getFilter(final Pipeline pipeline, final Class<T> clazz) {
        final List<T> filters = pipeline.findFilters(clazz);
        if (filters == null || filters.size() != 1) {
            throw new SearchException("Unable to find single '" + clazz.getName() + "' in search result pipeline");
        }
        return filters.get(0);
    }

    /**
     * Extract data from the segment list. Returns the total number of segments
     * that were successfully extracted.
     */
    private long processData(final long streamId, final long[] eventIds, final DocRef pipelineRef,
                             final Pipeline pipeline) {
        final ErrorReceiver errorReceiver = (severity, location, elementId, message, e) -> {
            task.getErrorReceiver().log(severity, location, elementId, message, e);
            throw ProcessException.wrap(message, e);
        };

        errorReceiverProxy.setErrorReceiver(errorReceiver);
        long count = 0;

        try {
            // Open the stream source.
            final StreamSource streamSource = streamStore.openStreamSource(streamId);
            if (streamSource != null) {
                try {
                    // This is a valid stream so try and extract as many
                    // segments as we are allowed.
                    try (final RASegmentInputStream segmentInputStream = new RASegmentInputStream(streamSource)) {
                        // Include the XML Header and footer.
                        segmentInputStream.include(0);
                        segmentInputStream.include(segmentInputStream.count() - 1);

                        // Include as many segments as we can.
                        for (final long segmentId : eventIds) {
                            segmentInputStream.include(segmentId);
                            count++;
                        }

                        // Now try and extract the data.
                        extract(pipelineRef, pipeline, streamSource, segmentInputStream, count);

                    } catch (final RuntimeException e) {
                        // Something went wrong extracting data from this
                        // stream.
                        error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(),
                                e);
                    }
                } catch (final IOException | RuntimeException e) {
                    // Something went wrong extracting data from this stream.
                    error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(), e);
                } finally {
                    streamStore.closeStreamSource(streamSource);
                }
            }
        } catch (final RuntimeException e) {
            // Something went wrong extracting data from this stream.
            error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * We do this one by one
     */
    private void extract(final DocRef pipelineRef, final Pipeline pipeline, final StreamSource source,
                         final RASegmentInputStream segmentInputStream, final long count) {
        if (source != null && segmentInputStream != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reading " + count + " segments from stream " + source.getStream().getId());
            }

            try {
                // Here we need to reload the feed as this will get the related
                // objects Translation etc
                feedHolder.setFeedName(source.getStream().getFeedName());

                // Setup the meta data holder.
                metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, pipelineStore));

                streamHolder.setStream(source.getStream());
                pipelineHolder.setPipeline(pipelineRef);

                final InputStream inputStream = new IgnoreCloseInputStream(segmentInputStream);

                // Get the encoding for the stream we are about to process.
                final String encoding = StreamUtil.DEFAULT_CHARSET_NAME;

                // Process the boundary.
                LAMBDA_LOGGER.logDurationIfDebugEnabled(
                        () -> pipeline.process(inputStream, encoding),
                        () -> LambdaLogger.buildMessage("Processing pipeline {}, stream {}",
                                pipelineRef.getUuid(), source.getStream().getId()));

            } catch (final TerminatedException e) {
                // Ignore stopped pipeline exceptions as we are meant to get
                // these when a task is asked to stop prematurely.
            } catch (final RuntimeException e) {
                throw SearchException.wrap(e);
            }
        }
    }

    private void error(final String message, final Throwable t) {
        task.getErrorReceiver().log(Severity.ERROR, null, null, message, t);
    }
}
