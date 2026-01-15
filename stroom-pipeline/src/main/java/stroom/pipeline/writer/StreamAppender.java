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

package stroom.pipeline.writer;

import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.WrappedSegmentOutputStream;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.pipeline.task.ProcessStatisticsFactory;
import stroom.pipeline.task.ProcessStatisticsFactory.ProcessStatistics;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.svg.shared.SvgImage;

import com.google.common.base.Strings;
import jakarta.inject.Inject;

import java.io.IOException;

@ConfigurableElement(
        type = "StreamAppender",
        category = Category.DESTINATION,
        description = """
                A destination used to write the output stream to a new stream in the stream store.
                The configuration allows for starting a new stream once a size threshold is reached.""",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class StreamAppender extends AbstractAppender {

    private final ErrorReceiverProxy errorReceiverProxy;
    private final Store streamStore;
    private final MetaHolder metaHolder;
    private final StreamProcessorHolder streamProcessorHolder;
    private final MetaData metaData;
    private final RecordCount recordCount;
    private final DocRefInfoService docRefInfoService;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    private DocRef feedRef;
    private String streamType;
    private boolean segmentOutput = true;
    private Target streamTarget;
    private long count;
    private String volumeGroup;

    private ProcessStatistics lastProcessStatistics;

    @Inject
    public StreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                          final Store streamStore,
                          final MetaHolder metaHolder,
                          final StreamProcessorHolder streamProcessorHolder,
                          final MetaData metaData,
                          final RecordCount recordCount,
                          final DocRefInfoService docRefInfoService,
                          final VolumeGroupNameProvider volumeGroupNameProvider) {
        super(errorReceiverProxy);
        this.errorReceiverProxy = errorReceiverProxy;
        this.streamStore = streamStore;
        this.metaHolder = metaHolder;
        this.streamProcessorHolder = streamProcessorHolder;
        this.metaData = metaData;
        this.recordCount = recordCount;
        this.docRefInfoService = docRefInfoService;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    @Override
    protected Output createOutput() {
        final Meta parentMeta = metaHolder.getMeta();

        String feed = null;
        if (feedRef != null) {
            feed = docRefInfoService.name(feedRef).orElse(null);
            if (Strings.isNullOrEmpty(feed)) {
                fatal("Feed not found");
            }

        } else if (parentMeta == null) {
            fatal("Unable to determine feed as no parent set");
        } else if (Strings.isNullOrEmpty(parentMeta.getFeedName())) {
            fatal("Parent has no feed name");
        } else {
            // Use current feed if none other has been specified.
            feed = parentMeta.getFeedName();
        }

        if (Strings.isNullOrEmpty(streamType)) {
            fatal("Stream type not specified");
        }

        final Processor processor = streamProcessorHolder.getStreamProcessor();
        final ProcessorTask processorTask = streamProcessorHolder.getStreamTask();

        String processorUuid = null;
        String processorFilterUuid = null;
        Integer processorFilterId = null;
        String pipelineUuid = null;
        Long processorTaskId = null;

        if (processor != null) {
            processorUuid = processor.getUuid();
            pipelineUuid = processor.getPipelineUuid();
        }
        if (processorTask != null) {
            processorTaskId = processorTask.getId();
            final ProcessorFilter processorFilter = processorTask.getProcessorFilter();
            if (processorFilter != null) {
                processorFilterUuid = processorFilter.getUuid();
                processorFilterId = processorFilter.getId();
            }
        }

        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feed)
                .typeName(streamType)
                .parent(parentMeta)
                .processorUuid(processorUuid)
                .pipelineUuid(pipelineUuid)
                .processorFilterId(processorFilterId)
                .processorTaskId(processorTaskId)
                .build();

        final String volumeGroupName = volumeGroupNameProvider
                .getVolumeGroupName(feed, streamType, volumeGroup);
        streamTarget = streamStore.openTarget(metaProperties, volumeGroupName);

        final WrappedSegmentOutputStream wrappedSegmentOutputStream =
                new WrappedSegmentOutputStream(streamTarget.next().get()) {
                    @Override
                    public void close() throws IOException {
                        super.flush();
                        super.close();
                        StreamAppender.this.close();
                    }
                };

        return new StreamOutput(wrappedSegmentOutputStream, segmentOutput);
    }

    @Override
    public Destination borrowDestination() throws IOException {
        count++;
        return super.borrowDestination();
    }

    private void close() {
        // Only do something if an output stream was used.
        if (streamTarget != null) {
            try {
                // See if the task has been terminated.
                checkTermination();

                // Write process meta data.
                streamTarget.getAttributes().putAll(metaData.getAttributes());

                // Get current process statistics
                final ProcessStatistics processStatistics = ProcessStatisticsFactory.create(recordCount,
                        errorReceiverProxy);
                // Diff the current statistics with the last captured statistics.
                final ProcessStatistics currentStatistics = processStatistics.subtract(lastProcessStatistics);
                // Set the last statistics.
                lastProcessStatistics = processStatistics;

                // Write statistics meta data.
                currentStatistics.write(streamTarget.getAttributes());

                // Overwrite the actual output record count.
                streamTarget.getAttributes().put(MetaFields.REC_WRITE.getFldName(), String.valueOf(count));

                // Close the stream target.
                try {
                    streamTarget.close();
                } catch (final IOException | RuntimeException e) {
                    try {
                        // Log the error.
                        fatal(e.getMessage());
                    } finally {
                        // Delete the output.
                        streamStore.deleteTarget(streamTarget);
                    }
                }

            } catch (final RuntimeException e) {

                // Delete the target.
                streamStore.deleteTarget(streamTarget);

                // Log the error.
                fatal("Terminated");

                throw e;
            }
        }
    }

    @PipelinePropertyDocRef(types = FeedDoc.TYPE)
    @PipelineProperty(
            description = "The feed that output stream should be written to. If not specified the feed the input " +
                    "stream belongs to will be used.",
            displayPriority = 2)
    public void setFeed(final DocRef feedRef) {
        this.feedRef = feedRef;
    }

    @PipelineProperty(
            description = "The stream type that the output stream should be written as. This must be specified.",
            displayPriority = 1)
    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    @PipelineProperty(
            description = "Should the output stream be marked with indexed segments to allow fast access to " +
                    "individual records?",
            defaultValue = "true",
            displayPriority = 3)
    public void setSegmentOutput(final boolean segmentOutput) {
        this.segmentOutput = segmentOutput;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(description = "When the current output stream exceeds this size it will be closed and a " +
            "new one created.",
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

    @PipelineProperty(description = "Choose if you want to split individual records into separate output streams.",
            defaultValue = "false",
            displayPriority = 6)
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    @PipelineProperty(
            description = "Optionally override the default volume group of the destination feed.",
            displayPriority = 7)
    public void setVolumeGroup(final String volumeGroup) {
        this.volumeGroup = volumeGroup;
    }
}
