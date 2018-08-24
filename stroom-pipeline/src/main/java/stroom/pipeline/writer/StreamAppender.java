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

import stroom.data.meta.api.AttributeMap;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.data.store.api.WrappedSegmentOutputStream;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.io.StreamCloser;
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
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.streamtask.shared.Processor;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

@ConfigurableElement(type = "StreamAppender", category = Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class StreamAppender extends AbstractAppender {
    private final ErrorReceiverProxy errorReceiverProxy;
    private final StreamStore streamStore;
    private final StreamHolder streamHolder;
    private final StreamProcessorHolder streamProcessorHolder;
    private final MetaData metaData;
    private final StreamCloser streamCloser;

    private String feed;
    private String streamType;
    private boolean segmentOutput = true;
    private StreamTarget streamTarget;

    private SegmentOutputStream segmentOutputStream;
    private boolean doneHeader;

    @Inject
    public StreamAppender(final ErrorReceiverProxy errorReceiverProxy,
                          final StreamStore streamStore,
                          final StreamHolder streamHolder,
                          final StreamProcessorHolder streamProcessorHolder,
                          final MetaData metaData,
                          final StreamCloser streamCloser) {
        super(errorReceiverProxy);
        this.errorReceiverProxy = errorReceiverProxy;
        this.streamStore = streamStore;
        this.streamHolder = streamHolder;
        this.streamProcessorHolder = streamProcessorHolder;
        this.metaData = metaData;
        this.streamCloser = streamCloser;
    }

    @Override
    protected OutputStream createOutputStream() {
        final Data parentStream = streamHolder.getStream();

        if (feed == null && parentStream != null && parentStream.getFeedName() != null) {
            feed = parentStream.getFeedName();
        }

        if (streamType == null) {
            errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), "Stream type not specified", null);
            throw new ProcessException("Stream type not specified");
        }

        Integer processorId = null;
        String pipelineUuid = null;
        Long streamTaskId = null;

        final Processor processor = streamProcessorHolder.getStreamProcessor();
        if (processor != null) {
            processorId = (int) processor.getId();
            pipelineUuid = processor.getPipelineUuid();
        }
        if (streamProcessorHolder.getStreamTask() != null) {
            streamTaskId = streamProcessorHolder.getStreamTask().getId();
        }

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(feed)
                .typeName(streamType)
                .parent(parentStream)
                .processorId(processorId)
                .pipelineUuid(pipelineUuid)
                .processorTaskId(streamTaskId)
                .build();

        streamTarget = streamStore.openStreamTarget(streamProperties);

        // Let the stream closer handle closing it
        streamCloser.add(streamTarget);

//        if (segmentOutput) {
        segmentOutputStream = new WrappedSegmentOutputStream(streamTarget.getOutputStreamProvider().next()) {
            @Override
            public void close() throws IOException {
                super.flush();
                super.close();
                StreamAppender.this.close();
            }
        };

//        } else {
//            targetOutputStream = new WrappedOutputStream(streamTarget.getOutputStream()) {
//                @Override
//                public void close() throws IOException {
//                    super.flush();
//                    super.close();
//                    StreamAppender.this.close();
//                }
//            };
//        }

        return segmentOutputStream;
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
    public void returnDestination(final Destination destination) throws IOException {
        // We assume that the parent will write an entire segment when it borrows a destination so add a segment marker
        // here after a segment is written.

        // Writing a segment marker here ensures there is always a marker written before the footer regardless or
        // whether a footer is actually written. We do this because we always make an allowance for a footer for data
        // display purposes.
        insertSegmentMarker();
    }

    private void insertSegmentMarker() throws IOException {
        // Add a segment marker to the output stream if we are segmenting.
        if (segmentOutput) {
            segmentOutputStream.addSegment();
        }
    }

    private void close() {
        // Only do something if an output stream was used.
        if (streamTarget != null) {
            // Write meta data.
            final AttributeMap attributeMap = metaData.getAttributeMap();
            streamTarget.getAttributes().putAll(attributeMap);
            // We leave the streamCloser to close the stream target as it may
            // want to delete it instead
        }
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
}
