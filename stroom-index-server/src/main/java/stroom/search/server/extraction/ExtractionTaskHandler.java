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

package stroom.search.server.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.filter.IdEnrichmentFilter;
import stroom.pipeline.server.filter.XMLFilter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.v2.DocRef;
import stroom.search.server.SearchException;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.util.List;

public class ExtractionTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionTaskHandler.class);

    private final StreamStore streamStore;
    private final FeedService feedService;
    private final FeedHolder feedHolder;
    private final CurrentUserHolder currentUserHolder;
    private final StreamHolder streamHolder;
    private final PipelineHolder pipelineHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineFactory pipelineFactory;
    private final PipelineService pipelineService;
    private final PipelineDataCache pipelineDataCache;
    private final TaskMonitor taskMonitor;
    private final SecurityContext securityContext;

    private ExtractionTask task;

    @Inject
    ExtractionTaskHandler(final StreamStore streamStore,
                          final FeedService feedService,
                          final FeedHolder feedHolder,
                          final CurrentUserHolder currentUserHolder,
                          final StreamHolder streamHolder,
                          final PipelineHolder pipelineHolder,
                          final ErrorReceiverProxy errorReceiverProxy,
                          final PipelineFactory pipelineFactory,
                          @Named("cachedPipelineService") final PipelineService pipelineService,
                          final PipelineDataCache pipelineDataCache,
                          final TaskMonitor taskMonitor,
                          final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.feedHolder = feedHolder;
        this.currentUserHolder = currentUserHolder;
        this.streamHolder = streamHolder;
        this.pipelineHolder = pipelineHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineFactory = pipelineFactory;
        this.pipelineService = pipelineService;
        this.pipelineDataCache = pipelineDataCache;
        this.taskMonitor = taskMonitor;
        this.securityContext = securityContext;
    }

    public VoidResult exec(final ExtractionTask task) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            taskMonitor.setName("Extraction");
            if (!taskMonitor.isTerminated()) {
                final String streamId = String.valueOf(task.getStreamId());
                taskMonitor.info("Extracting " + task.getEventIds().length + " records from stream " + streamId);

                extract(task);
            }
        }

        return VoidResult.INSTANCE;
    }

    private void extract(final ExtractionTask task) {
        try {
            this.task = task;

            // Set the current user.
            currentUserHolder.setCurrentUser(securityContext.getUserId());

            final DocRef pipelineRef = task.getPipelineRef();

            // Get the translation that will be used to display results.
            final PipelineEntity pipelineEntity = pipelineService.loadByUuid(pipelineRef.getUuid());
            if (pipelineEntity == null) {
                throw new SearchException("Unable to find result pipeline: " + pipelineRef);
            }

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
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
            processData(task.getStreamId(), task.getEventIds(), pipelineEntity, pipeline);

        } catch (final Exception e) {
            error(e.getMessage(), e);
        }
    }

    private <T extends XMLFilter> T getFilter(final Pipeline pipeline, final Class<T> clazz) {
        final List<T> filters = pipeline.findFilters(clazz);
        if (filters == null || filters.size() != 1) {
            throw new SearchException("Unable to find single '" + clazz.getName() + "' in search result pipeline");
        }
        final T filter = filters.get(0);
        return filter;
    }

    /**
     * Extract data from the segment list. Returns the total number of segments
     * that were successfully extracted.
     */
    private long processData(final long streamId, final long[] eventIds, final PipelineEntity pipelineEntity,
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
                        extract(pipelineEntity, pipeline, streamSource, segmentInputStream, count);

                    } catch (final Exception e) {
                        // Something went wrong extracting data from this
                        // stream.
                        error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(),
                                e);
                    }
                } catch (final Exception e) {
                    // Something went wrong extracting data from this stream.
                    error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(), e);
                } finally {
                    streamStore.closeStreamSource(streamSource);
                }
            }
        } catch (final Exception e) {
            // Something went wrong extracting data from this stream.
            error("Unable to extract data from stream source with id: " + streamId + " - " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * We do this one by one
     */
    private void extract(final PipelineEntity pipelineEntity, final Pipeline pipeline, final StreamSource source,
                         final RASegmentInputStream segmentInputStream, final long count) {
        if (source != null && segmentInputStream != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reading " + count + " segments from stream " + source.getStream().getId());
            }

            try {
                // Here we need to reload the feed as this will get the related
                // objects Translation etc
                final Feed feed = feedService.load(source.getStream().getFeed());
                feedHolder.setFeed(feed);
                streamHolder.setStream(source.getStream());
                pipelineHolder.setPipeline(pipelineEntity);

                final InputStream inputStream = new IgnoreCloseInputStream(segmentInputStream);

                // Get the encoding for the stream we are about to process.
                final String encoding = StreamUtil.DEFAULT_CHARSET_NAME;

                // Process the boundary.
                pipeline.process(inputStream, encoding);

            } catch (final TerminatedException e) {
                // Ignore stopped pipeline exceptions as we are meant to get
                // these when a task is asked to stop prematurely.
            } catch (final Exception e) {
                throw SearchException.wrap(e);
            }
        }
    }

    private void error(final String message, final Throwable t) {
        task.getErrorReceiver().log(Severity.ERROR, null, null, message, t);
    }
}
