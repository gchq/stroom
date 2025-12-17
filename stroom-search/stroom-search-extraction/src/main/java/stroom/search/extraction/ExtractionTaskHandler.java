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

package stroom.search.extraction;

import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.IdEnrichmentExpectedIds;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

public class ExtractionTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionTaskHandler.class);

    private final Store streamStore;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final CurrentUserHolder currentUserHolder;
    private final MetaHolder metaHolder;
    private final PipelineHolder pipelineHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineFactory pipelineFactory;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;
    private final IdEnrichmentExpectedIds idEnrichmentExpectedIds;
    private final ExtractionState extractionState;

    @Inject
    ExtractionTaskHandler(final Store streamStore,
                          final FeedHolder feedHolder,
                          final MetaDataHolder metaDataHolder,
                          final CurrentUserHolder currentUserHolder,
                          final MetaHolder metaHolder,
                          final PipelineHolder pipelineHolder,
                          final ErrorReceiverProxy errorReceiverProxy,
                          final PipelineFactory pipelineFactory,
                          final PipelineStore pipelineStore,
                          final SecurityContext securityContext,
                          final IdEnrichmentExpectedIds idEnrichmentExpectedIds,
                          final ExtractionState extractionState) {
        this.streamStore = streamStore;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.currentUserHolder = currentUserHolder;
        this.metaHolder = metaHolder;
        this.pipelineHolder = pipelineHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineFactory = pipelineFactory;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
        this.idEnrichmentExpectedIds = idEnrichmentExpectedIds;
        this.extractionState = extractionState;
    }

    public Meta extract(final TaskContext taskContext,
                        final QueryKey queryKey,
                        final long streamId,
                        final long[] eventIds,
                        final DocRef pipelineRef,
                        final ErrorConsumer errorConsumer,
                        final PipelineData pipelineData) throws DataException {
        Meta meta = null;

        // Open the stream source.
        try (final Source source = streamStore.openSource(streamId)) {
            if (source != null) {
                SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_TASK_HANDLER_EXTRACT);
                SearchProgressLog.add(queryKey, SearchPhase.EXTRACTION_TASK_HANDLER_EXTRACT_EVENTS, eventIds.length);

                taskContext.reset();
                taskContext.info(() -> "" +
                        "Extracting " +
                        eventIds.length +
                        " records from stream_id=" +
                        streamId);

                meta = source.getMeta();

                // Set the current user.
                currentUserHolder.setCurrentUser(securityContext.getUserIdentity());

                // Create the parser.
                final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);
                if (pipeline == null) {
                    throw new ExtractionException("Unable to create parser for pipeline: " + pipelineRef);
                }

                // Set up the id enrichment filter to try and recreate the conditions
                // present when the index was built. We need to do this because the
                // input stream is now filtered to only include events matched by
                // the search. This means that the event ids cannot be calculated by
                // just counting events.
                idEnrichmentExpectedIds.setStreamId(streamId);
                idEnrichmentExpectedIds.setEventIds(eventIds);

                // Process the stream segments.
                processData(queryKey, source, eventIds, pipelineRef, pipeline, errorConsumer);

                // Ensure count is the same.
                if (eventIds.length != extractionState.getCount()) {
                    LOGGER.debug(() -> "Extraction count mismatch");
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return meta;
    }

    /**
     * Extract data from the segment list. Returns the total number of segments
     * that were successfully extracted.
     */
    private void processData(final QueryKey queryKey,
                             final Source source,
                             final long[] eventIds,
                             final DocRef pipelineRef,
                             final Pipeline pipeline,
                             final ErrorConsumer errorConsumer) {
        final ErrorReceiver errorReceiver = (severity, location, elementId, message, errorType, e) -> {
            final Optional<TaskTerminatedException> optional = TaskTerminatedException.unwrap(e);
            if (optional.isPresent()) {
                throw optional.get();
            }

            final StoredError storedError = new StoredError(severity, location, elementId, message, errorType);
            errorConsumer.add(storedError::toString);
            throw ProcessException.wrap(message, e);
        };

        errorReceiverProxy.setErrorReceiver(errorReceiver);
        long count = 0;

        try (final InputStreamProvider inputStreamProvider = source.get(0)) {
            // This is a valid stream so try and extract as many
            // segments as we are allowed.
            try (final SegmentInputStream segmentInputStream = inputStreamProvider.get()) {
                // Include the XML Header and footer.
                segmentInputStream.include(0);
                segmentInputStream.include(segmentInputStream.count() - 1);

                // Include as many segments as we can.
                for (final long eventId : eventIds) {
                    segmentInputStream.include(eventId);
                    count++;
                }

                // Now try and extract the data.
                extract(queryKey, pipelineRef, pipeline, source, segmentInputStream, count);
            }
        } catch (final ExtractionException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            final Optional<TaskTerminatedException> optional = TaskTerminatedException.unwrap(e);
            if (optional.isPresent()) {
                LOGGER.debug(e::getMessage, e);
            } else {
                // Something went wrong extracting data from this stream.
                throw new ExtractionException("Unable to extract data from stream source with id: " +
                        source.getMeta().getId() + " - " + e.getMessage(), e);
            }
        }
    }

    /**
     * We do this one by one
     */
    private void extract(final QueryKey queryKey,
                         final DocRef pipelineRef,
                         final Pipeline pipeline,
                         final Source source,
                         final SegmentInputStream segmentInputStream,
                         final long count) {
        if (source != null && segmentInputStream != null) {
            LOGGER.debug(() -> "Reading " + count + " segments from stream " + source.getMeta().getId());

            SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_TASK_HANDLER_EXTRACT2);
            SearchProgressLog.add(queryKey, SearchPhase.EXTRACTION_TASK_HANDLER_EXTRACT2_EVENTS, count);

            try {
                // Here we need to reload the feed as this will get the related
                // objects Translation etc
                feedHolder.setFeedName(source.getMeta().getFeedName());

                // Set up the meta data holder.
                metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));

                metaHolder.setMeta(source.getMeta());
                pipelineHolder.setPipeline(pipelineRef);

                final InputStream inputStream = new IgnoreCloseInputStream(segmentInputStream);

                // Get the encoding for the stream we are about to process.
                final String encoding = StreamUtil.DEFAULT_CHARSET_NAME;

                // Process the boundary.
                LOGGER.logDurationIfDebugEnabled(
                        () -> pipeline.process(inputStream, encoding),
                        () -> LogUtil.message("Processing pipeline {}, stream {}",
                                pipelineRef.getUuid(), source.getMeta().getId()));

            } catch (final TaskTerminatedException e) {
                // Ignore stopped pipeline exceptions as we are meant to get
                // these when a task is asked to stop prematurely.
                LOGGER.debug("Swallowing TaskTerminatedException");
            } catch (final RuntimeException e) {
                throw ExtractionException.wrap(e);
            }
        }
    }
}
