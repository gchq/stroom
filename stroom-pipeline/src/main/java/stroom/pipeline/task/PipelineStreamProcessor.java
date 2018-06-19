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

package stroom.pipeline.task;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.FeedProperties;
import stroom.feed.MetaMap;
import stroom.io.StreamCloser;
import stroom.node.NodeCache;
import stroom.pipeline.DefaultErrorWriter;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamMetaService;
import stroom.data.meta.api.StreamProperties;
import stroom.data.meta.api.StreamStatus;
import stroom.data.meta.api.StreamDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.InclusiveRanges;
import stroom.streamtask.InclusiveRanges.InclusiveRange;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.StreamProcessorTaskExecutor;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.task.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.io.PreviewInputStream;
import stroom.util.io.WrappedOutputStream;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PipelineStreamProcessor implements StreamProcessorTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStreamProcessor.class);
    private static final String PROCESSING = "Processing:";
    private static final String FINISHED = "Finished:";
    private static final int PREVIEW_SIZE = 100;
    private static final int MIN_STREAM_SIZE = 1;
    private static final Pattern XML_DECL_PATTERN = Pattern.compile("<\\?\\s*xml[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final String INTERNAL_STAT_KEY_PIPELINE_STREAM_PROCESSOR = "pipelineStreamProcessor";

    private final PipelineFactory pipelineFactory;
    private final StreamStore streamStore;
    private final StreamMetaService streamMetaService;
    private final PipelineStore pipelineStore;
    private final TaskContext taskContext;
    private final PipelineHolder pipelineHolder;
    private final FeedHolder feedHolder;
    private final FeedProperties feedProperties;
    private final MetaDataHolder metaDataHolder;
    private final StreamHolder streamHolder;
    private final SearchIdHolder searchIdHolder;
    private final LocationFactoryProxy locationFactory;
    private final StreamProcessorHolder streamProcessorHolder;
    private final StreamProcessorService streamProcessorService;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final ErrorWriterProxy errorWriterProxy;
    private final MetaData metaData;
    private final RecordCount recordCount;
    private final StreamCloser streamCloser;
    private final RecordErrorReceiver recordErrorReceiver;
    private final NodeCache nodeCache;
    private final PipelineDataCache pipelineDataCache;
    private final InternalStatisticsReceiver internalStatisticsReceiver;

    private Processor streamProcessor;
    private ProcessorFilter streamProcessorFilter;
    private ProcessorFilterTask streamTask;
    private StreamSource streamSource;

    @Inject
    PipelineStreamProcessor(final PipelineFactory pipelineFactory,
                            final StreamStore streamStore,
                            final StreamMetaService streamMetaService,
                            @Named("cachedPipelineStore") final PipelineStore pipelineStore,
                            final TaskContext taskContext,
                            final PipelineHolder pipelineHolder,
                            final FeedHolder feedHolder,
                            final FeedProperties feedProperties,
                            final MetaDataHolder metaDataHolder,
                            final StreamHolder streamHolder,
                            final SearchIdHolder searchIdHolder,
                            final LocationFactoryProxy locationFactory,
                            final StreamProcessorHolder streamProcessorHolder,
                            final StreamProcessorService streamProcessorService,
                            final ErrorReceiverProxy errorReceiverProxy,
                            final ErrorWriterProxy errorWriterProxy,
                            final MetaData metaData,
                            final RecordCount recordCount,
                            final StreamCloser streamCloser,
                            final RecordErrorReceiver recordErrorReceiver,
                            final NodeCache nodeCache,
                            final PipelineDataCache pipelineDataCache,
                            final InternalStatisticsReceiver internalStatisticsReceiver) {
        this.pipelineFactory = pipelineFactory;
        this.streamStore = streamStore;
        this.streamMetaService = streamMetaService;
        this.pipelineStore = pipelineStore;
        this.taskContext = taskContext;
        this.pipelineHolder = pipelineHolder;
        this.feedHolder = feedHolder;
        this.feedProperties = feedProperties;
        this.metaDataHolder = metaDataHolder;
        this.streamHolder = streamHolder;
        this.searchIdHolder = searchIdHolder;
        this.locationFactory = locationFactory;
        this.streamProcessorHolder = streamProcessorHolder;
        this.streamProcessorService = streamProcessorService;
        this.errorReceiverProxy = errorReceiverProxy;
        this.errorWriterProxy = errorWriterProxy;
        this.metaData = metaData;
        this.recordCount = recordCount;
        this.streamCloser = streamCloser;
        this.recordErrorReceiver = recordErrorReceiver;
        this.nodeCache = nodeCache;
        this.pipelineDataCache = pipelineDataCache;
        this.internalStatisticsReceiver = internalStatisticsReceiver;
    }

    @Override
    public void exec(final Processor streamProcessor, final ProcessorFilter streamProcessorFilter,
                     final ProcessorFilterTask streamTask, final StreamSource streamSource) {
        try {
            this.streamProcessor = streamProcessor;
            this.streamProcessorFilter = streamProcessorFilter;
            this.streamTask = streamTask;
            this.streamSource = streamSource;

            // Setup the error handler and receiver.
            errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

            // Setup the error writer.
            final ProcessInfoOutputStreamProvider processInfoOutputStreamProvider = new ProcessInfoOutputStreamProvider(streamStore, streamCloser, metaData,
                    streamSource.getStream(), streamProcessor, streamTask);
            final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
            errorWriter.addOutputStreamProvider(processInfoOutputStreamProvider);
            errorWriterProxy.setErrorWriter(errorWriter);

            process();

        } catch (final RuntimeException e) {
            outputError(e);
        }
    }

    private void process() {
        // Record when processing began so we know how long it took
        // afterwards.
        final long startTime = System.currentTimeMillis();
        String feedName = null;
        PipelineDoc pipelineDoc = null;

        try {
            final Stream stream = streamSource.getStream();

            // Set the search id to be the id of the stream processor filter.
            // Only do this where the task has specific data ranges that need extracting as this is only the case with a batch search.
            if (streamProcessorFilter != null && streamTask.getData() != null && streamTask.getData().length() > 0) {
                searchIdHolder.setSearchId(Long.toString(streamProcessorFilter.getId()));
            }

            // Load the feed.
            feedName = stream.getFeedName();
            feedHolder.setFeedName(feedName);

            // Setup the meta data holder.
            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, pipelineStore));

            // Set the pipeline so it can be used by a filter if needed.
            pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.DOCUMENT_TYPE, streamProcessor.getPipelineUuid()));
            pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));

            // Create some processing info.
            final String info = "" +
                    " pipeline=" +
                    pipelineDoc.getName() +
                    ", feed=" +
                    feedName +
                    ", streamId=" +
                    stream.getId() +
                    ", streamCreated=" +
                    DateUtil.createNormalDateTimeString(stream.getCreateMs());

            // Create processing start message.
            final String processingInfo = PROCESSING + info;

            // Log that we are starting to process.
            taskContext.info(processingInfo);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(processingInfo);
            }

            // Hold the source and feed so the pipeline filters can get them.
            streamProcessorHolder.setStreamProcessor(streamProcessor, streamTask);

            // Process the streams.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactory.create(pipelineData);
            processNestedStreams(pipeline, stream, streamSource, feedName, stream.getStreamTypeName());

            // Create processing finished message.
            final String finishedInfo = "" +
                    FINISHED +
                    info +
                    ", finished in " +
                    ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime);

            // Log that we have finished processing.
            taskContext.info(finishedInfo);
            LOGGER.info(finishedInfo);

        } catch (final RuntimeException e) {
            outputError(e);

        } finally {
            // Check we are not superseded.
            checkSuperseded(startTime);

            // Record some statistics about processing.
            recordStats(feedName, pipelineDoc);

            // Update the meta data for all output streams to use.
            updateMetaData(streamSource);

            try {
                // Close all open streams.
                streamCloser.close();
            } catch (final IOException e) {
                outputError(e);
            }
        }
    }

    /**
     * Look for any other streams that have been produced by the same pipeline
     * and stream as the one we are processing. If we find any only the latest
     * stream task id is validate (which would normally be this stream task).
     * Any earlier stream tasks their streams should be deleted. If we are an
     * earlier stream task then mark our output as to be deleted (rather than
     * unlock it).
     */
    private void checkSuperseded(final long processStartTime) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.PARENT_STREAM_ID, Condition.EQUALS, String.valueOf(streamSource.getStream().getId()))
                .addTerm(StreamDataSource.STREAM_PROCESSOR_ID, Condition.EQUALS, String.valueOf(streamProcessor.getId()))
                .build();
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria(expression);
        final List<Stream> streamList = streamMetaService.find(findStreamCriteria);

        Long latestStreamTaskId = null;
        long latestStreamCreationTime = processStartTime;

        // Find the latest stream task .... this one is not superseded
        for (final Stream stream : streamList) {
            // TODO : @66 REMOVE STREAM TASK ID FROM STREAM AND QUERY THE STREAM PROCESSOR SERVICE TO FIND THE LATEST TASK ID FOR THE CURRENT INPUT STREAM AND PROCESSOR

            if (stream.getStreamTaskId() != null && !StreamStatus.DELETED.equals(stream.getStatus())) {
                if (stream.getCreateMs() > latestStreamCreationTime) {
                    latestStreamCreationTime = stream.getCreateMs();
                    latestStreamTaskId = stream.getStreamTaskId();
                } else if (stream.getCreateMs() == latestStreamCreationTime
                        && (latestStreamTaskId == null || stream.getStreamTaskId() > latestStreamTaskId)) {
                    latestStreamCreationTime = stream.getCreateMs();
                    latestStreamTaskId = stream.getStreamTaskId();
                }
            }
        }

        // We are not the latest stream task
        if (latestStreamTaskId != null && latestStreamTaskId != streamTask.getId()) {
            // Delete all our output
            streamCloser.setDelete(true);
        }

        // Loop around all the streams found above looking for ones to delete
        final FindStreamCriteria findDeleteStreamCriteria = new FindStreamCriteria();
        for (final Stream stream : streamList) {
            // If the stream is not associated with the latest stream task
            // and is not already deleted then select it for deletion.
            if ((latestStreamTaskId == null || !latestStreamTaskId.equals(stream.getStreamTaskId()))
                    && !StreamStatus.DELETED.equals(stream.getStatus())) {
                findDeleteStreamCriteria.obtainSelectedIdSet().add(stream.getId());
            }
        }
        // If we have found any to delete then delete them now.
        if (findDeleteStreamCriteria.obtainSelectedIdSet().isConstrained()) {
            final long deleteCount = streamMetaService.findDelete(findDeleteStreamCriteria);
            LOGGER.info("checkSuperseded() - Removed {}", deleteCount);
        }
    }

    private void recordStats(final String feedName, final PipelineDoc pipelineDoc) {
        try {
            InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat(
                    INTERNAL_STAT_KEY_PIPELINE_STREAM_PROCESSOR,
                    System.currentTimeMillis(),
                    ImmutableMap.of(
                            "Feed", feedName,
                            "Pipeline", pipelineDoc.getName(),
                            "Node", nodeCache.getDefaultNode().getName()));

            internalStatisticsReceiver.putEvent(event);

        } catch (final RuntimeException e) {
            LOGGER.error("recordStats", e);
        }
    }

    public long getRead() {
        return recordCount.getRead();
    }

    public long getWritten() {
        return recordCount.getWritten();
    }

    public long getMarkerCount(final Severity... severity) {
        long count = 0;
        if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
            final ErrorStatistics statistics = (ErrorStatistics) errorReceiverProxy.getErrorReceiver();
            for (final Severity sev : severity) {
                count += statistics.getRecords(sev);
            }
        }
        return count;
    }

    /**
     * Processes a source and writes the result to a target.
     */
    private void processNestedStreams(final Pipeline pipeline,
                                      final Stream stream,
                                      final StreamSource streamSource,
                                      final String feedName,
                                      final String streamTypeName) {
        try {
            boolean startedProcessing = false;

            // Get the stream providers.
            streamHolder.setStream(stream);
            streamHolder.addProvider(streamSource);
            streamHolder.addProvider(streamSource.getChildStream(StreamTypeNames.META));
            streamHolder.addProvider(streamSource.getChildStream(StreamTypeNames.CONTEXT));

            // Get the main stream provider.
            final StreamSourceInputStreamProvider mainProvider = streamHolder.getProvider(streamTypeName);

            try {
                final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
                locationFactory.setLocationFactory(streamLocationFactory);

                // Loop over the stream boundaries and process each
                // sequentially.
                final long streamCount = mainProvider.getStreamCount();
                for (long streamNo = 0; streamNo < streamCount && !Thread.currentThread().isInterrupted(); streamNo++) {
                    InputStream inputStream;

                    // If the task requires specific events to be processed then
                    // add them.
                    final String data = streamTask.getData();
                    if (data != null && !data.isEmpty()) {
                        final List<InclusiveRange> ranges = InclusiveRanges.rangesFromString(data);
                        final RASegmentInputStream raSegmentInputStream = mainProvider.getSegmentInputStream(streamNo);
                        raSegmentInputStream.include(0);
                        for (final InclusiveRange range : ranges) {
                            for (long i = range.getMin(); i <= range.getMax(); i++) {
                                raSegmentInputStream.include(i);
                            }
                        }
                        raSegmentInputStream.include(raSegmentInputStream.count() - 1);
                        inputStream = raSegmentInputStream;

                    } else {
                        // Get the stream.
                        inputStream = mainProvider.getStream(streamNo);
                    }

                    // Get the appropriate encoding for the stream type.
                    final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

                    // We want to get a preview of the input stream so we can
                    // skip it if it is effectively empty.
                    final PreviewInputStream previewInputStream = new PreviewInputStream(inputStream);
                    String preview = previewInputStream.previewAsString(PREVIEW_SIZE, encoding);
                    // Remove whitespace from the preview.
                    preview = preview.trim();

                    // If there are still characters in the preview then
                    // continue.
                    if (preview.length() >= MIN_STREAM_SIZE) {
                        // Try and remove XML declaration for cases where the
                        // input is blank except for an XML declaration.
                        preview = XML_DECL_PATTERN.matcher(preview).replaceFirst("");
                        // Remove whitespace from the preview.
                        preview = preview.trim();

                        // Skip the input stream if it is empty. replaces:
                        // inputStream.size >= MIN_STREAM_SIZE
                        if (preview.length() >= MIN_STREAM_SIZE) {
                            // Start processing if we haven't already.
                            if (!startedProcessing) {
                                startedProcessing = true;
                                pipeline.startProcessing();
                            }

                            streamHolder.setStreamNo(streamNo);
                            streamLocationFactory.setStreamNo(streamNo + 1);

                            // Process the boundary.
                            try {
                                pipeline.process(previewInputStream, encoding);
                            } catch (final LoggedException e) {
                                // The exception has already been logged so
                                // ignore it.
                                if (LOGGER.isTraceEnabled() && stream != null) {
                                    LOGGER.trace("Error while processing stream task: id = " + stream.getId(), e);
                                }
                            } catch (final RuntimeException e) {
                                outputError(e);
                            }

                            // Reset the error statistics for the next stream.
                            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).reset();
                            }
                        }
                    }
                }
            } catch (final LoggedException e) {
                // The exception has already been logged so ignore it.
                if (LOGGER.isTraceEnabled() && stream != null) {
                    LOGGER.trace("Error while processing stream task: id = " + stream.getId(), e);
                }
            } catch (final IOException | RuntimeException e) {
                // An exception that's gets here is definitely a failure.
                outputError(e);

            } finally {
                // Update the meta data for all output streams to use
                updateMetaData(streamSource);

                try {
                    if (startedProcessing) {
                        pipeline.endProcessing();
                    }
                } catch (final LoggedException e) {
                    // The exception has already been logged so ignore it.
                    if (LOGGER.isTraceEnabled() && stream != null) {
                        LOGGER.trace("Error while processing stream task: id = " + stream.getId(), e);
                    }
                } catch (final RuntimeException e) {
                    outputError(e);
                }
            }
        } catch (final IOException | RuntimeException e) {
            outputError(e);
        }
    }

    private void updateMetaData(final StreamSource source) {
        try {
            // Write some meta data to the map for all output streams to use
            // when they close.
            metaData.put("Source Stream", String.valueOf(source.getStream().getId()));
            metaData.put(StreamDataSource.REC_READ, String.valueOf(recordCount.getRead()));
            metaData.put(StreamDataSource.REC_WRITE, String.valueOf(recordCount.getWritten()));
            metaData.put(StreamDataSource.REC_INFO, String.valueOf(getMarkerCount(Severity.INFO)));
            metaData.put(StreamDataSource.REC_WARN, String.valueOf(getMarkerCount(Severity.WARNING)));
            metaData.put(StreamDataSource.REC_ERROR, String.valueOf(getMarkerCount(Severity.ERROR)));
            metaData.put(StreamDataSource.REC_FATAL, String.valueOf(getMarkerCount(Severity.FATAL_ERROR)));
            metaData.put(StreamDataSource.DURATION, String.valueOf(recordCount.getDuration()));

            // TODO : @66 DO WE REALLY NEED TO KNOW WHAT NODE PROCESSED A STREAM AS THE DATA IS AVAILABLE ON STREAM TASK???
//            metaData.put(StreamAttributeConstants.NODE, nodeCache.getDefaultNode().getName());
        } catch (final RuntimeException e) {
            outputError(e);
        }
    }

    private void outputError(final Exception e) {
        outputError(e, Severity.FATAL_ERROR);
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Exception e, final Severity severity) {
        if (errorReceiverProxy != null && !(e instanceof LoggedException)) {
            try {
                if (e.getMessage() != null) {
                    errorReceiverProxy.log(severity, null, "PipelineStreamProcessor", e.getMessage(), e);
                } else {
                    errorReceiverProxy.log(severity, null, "PipelineStreamProcessor", e.toString(), e);
                }
            } catch (final RuntimeException e2) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }

            if (LOGGER.isTraceEnabled() && streamSource.getStream() != null) {
                LOGGER.trace("Error while processing stream task: id = " + streamSource.getStream().getId(), e);
            }
        } else {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(streamSource.getStream());
    }

    private static class ProcessInfoOutputStreamProvider extends AbstractElement
            implements DestinationProvider, Destination {
        private final StreamStore streamStore;
        private final StreamCloser streamCloser;
        private final MetaData metaData;
        private final Stream stream;
        private final Processor streamProcessor;
        private final ProcessorFilterTask streamTask;

        private OutputStream processInfoOutputStream;
        private StreamTarget processInfoStreamTarget;

        ProcessInfoOutputStreamProvider(final StreamStore streamStore, final StreamCloser streamCloser,
                                        final MetaData metaData, final Stream stream, final Processor streamProcessor,
                                        final ProcessorFilterTask streamTask) {
            this.streamStore = streamStore;
            this.streamCloser = streamCloser;
            this.metaData = metaData;
            this.stream = stream;
            this.streamProcessor = streamProcessor;
            this.streamTask = streamTask;
        }

        @Override
        public Destination borrowDestination() {
            return this;
        }

        @Override
        public void returnDestination(final Destination destination) {
        }

        @Override
        public OutputStream getByteArrayOutputStream() {
            return getOutputStream(null, null);
        }

        @Override
        public OutputStream getOutputStream(final byte[] header, final byte[] footer) {
            if (processInfoOutputStream == null) {
                Integer processorId = null;
                String pipelineUuid = null;
                Long streamTaskId = null;

                if (streamProcessor != null) {
                    processorId = (int) streamProcessor.getId();
                    pipelineUuid = streamProcessor.getPipelineUuid();
                }
                if (streamTask != null) {
                    streamTaskId = streamTask.getId();
                }

                // Create a processing info stream to write all processing
                // information to.
                final StreamProperties errorStreamProperties = new StreamProperties.Builder()
                        .feedName(stream.getFeedName())
                        .streamTypeName(StreamTypeNames.ERROR)
                        .parent(stream)
                        .streamProcessorId(processorId)
                        .pipelineUuid(pipelineUuid)
                        .streamTaskId(streamTaskId)
                        .build();

                processInfoStreamTarget = streamStore.openStreamTarget(errorStreamProperties);
                streamCloser.add(processInfoStreamTarget);

                processInfoOutputStream = new WrappedOutputStream(processInfoStreamTarget.getOutputStream()) {
                    @Override
                    public void close() throws IOException {
                        super.flush();
                        super.close();

                        // Only do something if an output stream was used.
                        if (processInfoStreamTarget != null) {
                            // Write meta data.
                            final MetaMap metaMap = metaData.getMetaMap();
                            processInfoStreamTarget.getAttributeMap().putAll(metaMap);
                            // We let the streamCloser close the stream target
                            // with the stream store as it may want to delete it
                        }
                    }
                };
                streamCloser.add(processInfoOutputStream);
            }

            return processInfoOutputStream;
        }

        @Override
        public List<stroom.pipeline.factory.Processor> createProcessors() {
            return Collections.emptyList();
        }
    }
}
