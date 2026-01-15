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

package stroom.analytics.impl;

import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.search.extraction.ExtractionException;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;

public class AnalyticsStreamProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsStreamProcessor.class);

    private final Store streamStore;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final CurrentUserHolder currentUserHolder;
    private final MetaHolder metaHolder;
    private final PipelineHolder pipelineHolder;
    private final PipelineFactory pipelineFactory;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;

    @Inject
    AnalyticsStreamProcessor(final Store streamStore,
                             final FeedHolder feedHolder,
                             final MetaDataHolder metaDataHolder,
                             final CurrentUserHolder currentUserHolder,
                             final MetaHolder metaHolder,
                             final PipelineHolder pipelineHolder,
                             final PipelineFactory pipelineFactory,
                             final PipelineStore pipelineStore,
                             final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.currentUserHolder = currentUserHolder;
        this.metaHolder = metaHolder;
        this.pipelineHolder = pipelineHolder;
        this.pipelineFactory = pipelineFactory;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
    }

    public Meta extract(final TaskContext taskContext,
                        final long streamId,
                        final DocRef pipelineRef,
                        final PipelineData pipelineData) throws DataException {
        Meta meta = null;

        // Open the stream source.
        try (final Source source = streamStore.openSource(streamId)) {
            if (source != null) {
                taskContext.reset();
                taskContext.info(() -> "Extracting from stream meta_id=" + streamId);

                meta = source.getMeta();

                // Set the current user.
                currentUserHolder.setCurrentUser(securityContext.getUserIdentity());

                // Create the parser.
                final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);
                if (pipeline == null) {
                    throw new ExtractionException("Unable to create parser for pipeline: " + pipelineRef);
                }

                // Process the stream segments.
                processData(source, pipelineRef, pipeline);
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
    private void processData(final Source source,
                             final DocRef pipelineRef,
                             final Pipeline pipeline) {
        final long count = 0;

        try (final InputStreamProvider inputStreamProvider = source.get(0)) {
            // This is a valid stream so try and extract as many
            // segments as we are allowed.
            try (final SegmentInputStream segmentInputStream = inputStreamProvider.get()) {
                // Now try and extract the data.
                extract(pipelineRef, pipeline, source, segmentInputStream, count);
            }
        } catch (final TaskTerminatedException | ClosedByInterruptException e) {
            LOGGER.debug(e::getMessage, e);
        } catch (final ExtractionException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            // Something went wrong extracting data from this stream.
            throw new ExtractionException("Unable to extract data from stream source with id: " +
                    source.getMeta().getId() + " - " + e.getMessage(), e);
        }
    }

    /**
     * We do this one by one
     */
    private void extract(final DocRef pipelineRef,
                         final Pipeline pipeline,
                         final Source source,
                         final SegmentInputStream segmentInputStream,
                         final long count) {
        if (source != null && segmentInputStream != null) {
            LOGGER.debug(() -> "Reading " + count + " segments from stream " + source.getMeta().getId());

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
            } catch (final RuntimeException e) {
                throw ExtractionException.wrap(e);
            }
        }
    }
}
