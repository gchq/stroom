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

package stroom.pipeline.writer;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.WrappedSegmentOutputStream;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.pipeline.task.ProcessStatisticsFactory;
import stroom.pipeline.task.ProcessStatisticsFactory.ProcessStatistics;
import stroom.pipeline.task.SupersededOutputHelper;
import stroom.processor.shared.Processor;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

@ConfigurableElement(type = "StreamAppender", category = Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class StreamAppender extends AbstractAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final Store streamStore;
    private final MetaHolder metaHolder;
    private final StreamProcessorHolder streamProcessorHolder;
    private final MetaData metaData;
    private final RecordCount recordCount;
    private final SupersededOutputHelper supersededOutputHelper;

    private String feed;
    private String streamType;
    private boolean segmentOutput = true;
    private Target streamTarget;
    private WrappedSegmentOutputStream wrappedSegmentOutputStream;
    private boolean doneHeader;
    private long count;

    private ProcessStatistics lastProcessStatistics;

    @Inject
    public StreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                          final Store streamStore,
                          final MetaHolder metaHolder,
                          final StreamProcessorHolder streamProcessorHolder,
                          final MetaData metaData,
                          final RecordCount recordCount,
                          final SupersededOutputHelper supersededOutputHelper) {
        super(errorReceiverProxy);
        this.errorReceiverProxy = errorReceiverProxy;
        this.streamStore = streamStore;
        this.metaHolder = metaHolder;
        this.streamProcessorHolder = streamProcessorHolder;
        this.metaData = metaData;
        this.recordCount = recordCount;
        this.supersededOutputHelper = supersededOutputHelper;
    }

    @Override
    protected OutputStream createOutputStream() {
        final Meta parentMeta = metaHolder.getMeta();

        if (Strings.isNullOrEmpty(feed)) {
            if (parentMeta == null) {
                fatal("Unable to determine feed as no parent set");
            } else if (Strings.isNullOrEmpty(parentMeta.getFeedName())) {
                fatal("Parent has no feed name");
            } else {
                feed = parentMeta.getFeedName();
            }
        }

        if (Strings.isNullOrEmpty(feed)) {
            fatal("Feed not specified");
        }
        if (Strings.isNullOrEmpty(streamType)) {
            fatal("Stream type not specified");
        }

        String processorUuid = null;
        String pipelineUuid = null;
        Long streamTaskId = null;

        final Processor processor = streamProcessorHolder.getStreamProcessor();
        if (processor != null) {
            processorUuid = processor.getUuid();
            pipelineUuid = processor.getPipelineUuid();
        }
        if (streamProcessorHolder.getStreamTask() != null) {
            streamTaskId = streamProcessorHolder.getStreamTask().getId();
        }

        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feed)
                .typeName(streamType)
                .parent(parentMeta)
                .processorUuid(processorUuid)
                .pipelineUuid(pipelineUuid)
                .build();

        streamTarget = supersededOutputHelper.addTarget(() -> streamStore.openTarget(metaProperties));

        wrappedSegmentOutputStream = new WrappedSegmentOutputStream(streamTarget.next().get()) {
            @Override
            public void close() throws IOException {
                super.flush();
                super.close();
                StreamAppender.this.close();
            }
        };
        return wrappedSegmentOutputStream;
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        final OutputStream outputStream = super.getOutputStream(header, footer);
        if (!doneHeader) {
            doneHeader = true;
            insertSegmentMarker();
        }
        return outputStream;
    }

    @Override
    public Destination borrowDestination() throws IOException {
        count++;
        return super.borrowDestination();
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        // We assume that the parent will write an entire segment when it borrows a destination so add a segment marker
        // here after a segment is written.

        // Writing a segment marker here ensures there is always a marker written before the footer regardless or
        // whether a footer is actually written. We do this because we always make an allowance for a footer for data
        // display purposes.
        insertSegmentMarker();

        super.returnDestination(destination);
    }

    private void insertSegmentMarker() throws IOException {
        // Add a segment marker to the output stream if we are segmenting.
        if (segmentOutput) {
            wrappedSegmentOutputStream.addSegment();
        }
    }

    private void close() {
        // Only do something if an output stream was used.
        if (streamTarget != null) {
            // Write process meta data.
            streamTarget.getAttributes().putAll(metaData.getAttributes());

            // Get current process statistics
            final ProcessStatistics processStatistics = ProcessStatisticsFactory.create(recordCount, errorReceiverProxy);
            // Diff the current statistics with the last captured statistics.
            final ProcessStatistics currentStatistics = processStatistics.substract(lastProcessStatistics);
            // Set the last statistics.
            lastProcessStatistics = processStatistics;

            // Write statistics meta data.
            currentStatistics.write(streamTarget.getAttributes());

            // Overwrite the actual output record count.
            streamTarget.getAttributes().put(MetaFields.REC_WRITE.getName(), String.valueOf(count));

            // Close the stream target.
            try {
                if (supersededOutputHelper.isSuperseded()) {
                    streamStore.deleteTarget(streamTarget);
                } else {
                    streamTarget.close();
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    @Override
    long getCurrentOutputSize() {
        if (wrappedSegmentOutputStream != null) {
            return wrappedSegmentOutputStream.getPosition();
        }
        return 0;
    }

    @PipelinePropertyDocRef(types = FeedDoc.DOCUMENT_TYPE)
    @PipelineProperty(
            description = "The feed that output stream should be written to. If not specified the feed the input stream belongs to will be used.",
            displayPriority = 2)
    public void setFeed(final DocRef feedRef) {
        this.feed = feedRef.getName();
    }

    @PipelineProperty(
            description = "The stream type that the output stream should be written as. This must be specified.",
            displayPriority = 1)
    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    @PipelineProperty(
            description = "Should the output stream be marked with indexed segments to allow fast access to individual records?",
            defaultValue = "true",
            displayPriority = 3)
    public void setSegmentOutput(final boolean segmentOutput) {
        this.segmentOutput = segmentOutput;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(description = "When the current output stream exceeds this size it will be closed and a new one created.",
            displayPriority = 4)
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @PipelineProperty(description = "Choose if you want to split aggregated streams into separate output streams.",
            defaultValue = "false",
            displayPriority = 5)
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    private void fatal(final String message) {
        errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), message, null);
        throw new ProcessException(message);
    }
}
