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

package stroom.pipeline.server.task;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.MetaMap;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.io.StreamCloser;
import stroom.node.server.NodeCache;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.server.DefaultErrorWriter;
import stroom.pipeline.server.EncodingSelection;
import stroom.pipeline.server.ErrorWriterProxy;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.PipelineEntityService;
import stroom.pipeline.server.StreamLocationFactory;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ErrorStatistics;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.errorhandler.RecordErrorReceiver;
import stroom.pipeline.server.factory.AbstractElement;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.factory.Processor;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsFacadeFactory;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.InclusiveRanges;
import stroom.streamtask.server.InclusiveRanges.InclusiveRange;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;
import stroom.util.date.DateUtil;
import stroom.util.io.PreviewInputStream;
import stroom.util.io.WrappedOutputStream;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Scope(StroomScope.PROTOTYPE)
public class PipelineStreamProcessor implements StreamProcessorTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStreamProcessor.class);
    private static final String PROCESSING = "Processing:";
    private static final String FINISHED = "Finished:";
    private static final int PREVIEW_SIZE = 100;
    private static final int MIN_STREAM_SIZE = 1;
    private static final Pattern XML_DECL_PATTERN = Pattern.compile("<\\?\\s*xml[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final String INTERNAL_STAT_KEY_PIPELINE_STREAM_PROCESSOR = "pipelineStreamProcessor";

    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private StreamStore streamStore;
    @Resource(name = "cachedFeedService")
    private FeedService feedService;
    @Resource(name = "cachedPipelineEntityService")
    private PipelineEntityService pipelineEntityService;
    @Resource
    private TaskMonitor taskMonitor;
    @Resource
    private PipelineHolder pipelineHolder;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private StreamHolder streamHolder;
    @Resource
    private SearchIdHolder searchIdHolder;
    @Resource
    private LocationFactoryProxy locationFactory;
    @Resource
    private StreamProcessorHolder streamProcessorHolder;
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;
    @Resource
    private ErrorWriterProxy errorWriterProxy;
    @Resource
    private MetaData metaData;
    @Resource
    private RecordCount recordCount;
    @Resource
    private StreamCloser streamCloser;
    @Resource
    private RecordErrorReceiver recordErrorReceiver;
    @Resource
    private NodeCache nodeCache;
    @Resource
    private PipelineDataCache pipelineDataCache;
    @Resource
    private InternalStatisticsFacadeFactory internalStatisticsFacadeFactory;

    private StreamProcessor streamProcessor;
    private StreamProcessorFilter streamProcessorFilter;
    private StreamTask streamTask;
    private StreamSource streamSource;
    private ProcessInfoOutputStreamProvider processInfoOutputStreamProvider;

    @Override
    public void exec(final StreamProcessor streamProcessor, final StreamProcessorFilter streamProcessorFilter,
                     final StreamTask streamTask, final StreamSource streamSource) {
        try {
            this.streamProcessor = streamProcessor;
            this.streamProcessorFilter = streamProcessorFilter;
            this.streamTask = streamTask;
            this.streamSource = streamSource;

            // Setup the error handler and receiver.
            errorReceiverProxy.setErrorReceiver(recordErrorReceiver);

            // Setup the error writer.
            processInfoOutputStreamProvider = new ProcessInfoOutputStreamProvider(streamStore, streamCloser, metaData,
                    streamSource.getStream(), streamProcessor, streamTask);
            final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
            errorWriter.addOutputStreamProvider(processInfoOutputStreamProvider);
            errorWriterProxy.setErrorWriter(errorWriter);

            process();

        } catch (final Exception e) {
            outputError(e);
        }
    }

    private void process() {
        try {
            final Stream stream = streamSource.getStream();

            // Set the search id to be the id of the stream processor filter.
            if (streamProcessorFilter != null) {
                searchIdHolder.setSearchId(Long.toString(streamProcessorFilter.getId()));
            }

            // Load the feed.
            final Feed feed = feedService.load(stream.getFeed());
            feedHolder.setFeed(feed);

            // Set the pipeline so it can be used by a filter if needed.
            final PipelineEntity pipelineEntity = pipelineEntityService.load(streamProcessor.getPipeline());
            pipelineHolder.setPipeline(pipelineEntity);

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.getOrCreate(pipelineEntity);
            final Pipeline pipeline = pipelineFactory.create(pipelineData);

            // Create some processing info.
            final StringBuilder infoSb = new StringBuilder();
            infoSb.append(" pipeline=");
            infoSb.append(pipelineEntity.getName());
            infoSb.append(", feed=");
            infoSb.append(feed.getName());
            infoSb.append(", streamId=");
            infoSb.append(stream.getId());
            infoSb.append(", streamCreated=");
            infoSb.append(DateUtil.createNormalDateTimeString(stream.getCreateMs()));
            final String info = infoSb.toString();

            // Create processing start message.
            final StringBuilder processingInfoSb = new StringBuilder();
            processingInfoSb.append(PROCESSING);
            processingInfoSb.append(info);
            final String processingInfo = processingInfoSb.toString();

            // Log that we are starting to process.
            taskMonitor.info(processingInfo);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(processingInfo);
            }

            // Record when processing began so we know how long it took
            // afterwards.
            final long startTime = System.currentTimeMillis();

            // Hold the source and feed so the pipeline filters can get them.
            streamProcessorHolder.setStreamProcessor(streamProcessor, streamTask);
            feedHolder.setFeed(feed);

            // Process the streams.
            processNestedStreams(pipeline, stream, streamSource, feed, stream.getStreamType());

            // Create processing finished message.
            final StringBuilder finishedInfoSb = new StringBuilder();
            finishedInfoSb.append(FINISHED);
            finishedInfoSb.append(info);
            finishedInfoSb.append(", finished in ");
            finishedInfoSb.append(ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
            final String finishedInfo = finishedInfoSb.toString();

            // Log that we have finished processing.
            taskMonitor.info(finishedInfo);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(finishedInfo);
            }

            // Check we are not superseded
            checkSuperseded(startTime);

            recordStats(feed, pipelineEntity);

        } catch (final Exception e) {
            outputError(e);

        } finally {
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
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainParentStreamIdSet().add(streamSource.getStream());
        findStreamCriteria.obtainStatusSet().setMatchAll(true);
        findStreamCriteria.obtainStreamProcessorIdSet().add(streamProcessor);

        final List<Stream> streamList = streamStore.find(findStreamCriteria);

        Long latestStreamTaskId = null;
        long latestStreamCreationTime = processStartTime;

        // Find the latest stream task .... this one is not superseded
        for (final Stream stream : streamList) {
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
                findDeleteStreamCriteria.obtainStreamIdSet().add(stream);
            }
        }
        // If we have found any to delete then delete them now.
        if (findDeleteStreamCriteria.obtainStreamIdSet().isConstrained()) {
            final long deleteCount = streamStore.findDelete(findDeleteStreamCriteria);
            LOGGER.info("checkSuperseded() - Removed {}", deleteCount);
        }
    }

    private void recordStats(final Feed feed, final PipelineEntity pipelineEntity) {
        try {
            InternalStatisticEvent event = InternalStatisticEvent.createPlusOneCountStat(
                    INTERNAL_STAT_KEY_PIPELINE_STREAM_PROCESSOR,
                    System.currentTimeMillis(),
                    ImmutableMap.of(
                            "Feed", feed.getName(),
                            "Pipeline", pipelineEntity.getName(),
                            "Node", nodeCache.getDefaultNode().getName()));

            internalStatisticsFacadeFactory.create().putEvent(event);

        } catch (final Exception ex) {
            LOGGER.error("recordStats", ex);
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
    private void processNestedStreams(final Pipeline pipeline, final Stream stream, final StreamSource streamSource,
                                      final Feed feed, final StreamType streamType) {
        try {
            boolean startedProcessing = false;

            // Get the stream providers.
            streamHolder.setStream(stream);
            streamHolder.addProvider(streamSource);
            streamHolder.addProvider(streamSource.getChildStream(StreamType.META));
            streamHolder.addProvider(streamSource.getChildStream(StreamType.CONTEXT));

            // Get the main stream provider.
            final StreamSourceInputStreamProvider mainProvider = streamHolder.getProvider(streamType);

            try {
                final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
                locationFactory.setLocationFactory(streamLocationFactory);

                // Loop over the stream boundaries and process each
                // sequentially.
                final long streamCount = mainProvider.getStreamCount();
                for (long streamNo = 0; streamNo < streamCount && !taskMonitor.isTerminated(); streamNo++) {
                    InputStream inputStream = null;

                    // If the task requires specific events to be processed then
                    // add them.
                    final String data = streamTask.getData();
                    if (data != null && data.length() > 0) {
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
                    final String encoding = EncodingSelection.select(feed, streamType);

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
                            } catch (final Exception e) {
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
            } catch (final Exception e) {
                // An exception that's gets here is definitely a failure.
                outputError(e);

            } finally {
                try {
                    // Update the meta data for all output streams to use
                    updateMetaData(streamSource);

                } catch (final Exception e) {
                    outputError(e);

                } finally {
                    try {
                        if (startedProcessing) {
                            pipeline.endProcessing();
                        }
                    } catch (final LoggedException e) {
                        // The exception has already been logged so ignore it.
                        if (LOGGER.isTraceEnabled() && stream != null) {
                            LOGGER.trace("Error while processing stream task: id = " + stream.getId(), e);
                        }
                    } catch (final Exception e) {
                        outputError(e);
                    }
                }
            }
        } catch (final Exception e) {
            outputError(e);
        }
    }

    private void updateMetaData(final StreamSource source) {
        try {
            // Write some meta data to the map for all output streams to use
            // when they close.
            metaData.put("Source Stream", String.valueOf(source.getStream().getId()));
            metaData.put(StreamAttributeConstants.REC_READ, String.valueOf(recordCount.getRead()));
            metaData.put(StreamAttributeConstants.REC_WRITE, String.valueOf(recordCount.getWritten()));
            metaData.put(StreamAttributeConstants.REC_INFO, String.valueOf(getMarkerCount(Severity.INFO)));
            metaData.put(StreamAttributeConstants.REC_WARN, String.valueOf(getMarkerCount(Severity.WARNING)));
            metaData.put(StreamAttributeConstants.REC_ERROR, String.valueOf(getMarkerCount(Severity.ERROR)));
            metaData.put(StreamAttributeConstants.REC_FATAL, String.valueOf(getMarkerCount(Severity.FATAL_ERROR)));
            metaData.put(StreamAttributeConstants.DURATION, String.valueOf(recordCount.getDuration()));
            metaData.put(StreamAttributeConstants.NODE, nodeCache.getDefaultNode().getName());
        } catch (final Exception e) {
            outputError(e);
        }
    }

    private void outputError(final Exception ex) {
        outputError(ex, Severity.FATAL_ERROR);
    }

    /**
     * Used to handle any errors that may occur during translation.
     */
    private void outputError(final Exception ex, final Severity severity) {
        if (errorReceiverProxy != null && !(ex instanceof LoggedException)) {
            try {
                if (ex.getMessage() != null) {
                    errorReceiverProxy.log(severity, null, "PipelineStreamProcessor", ex.getMessage(), ex);
                } else {
                    errorReceiverProxy.log(severity, null, "PipelineStreamProcessor", ex.toString(), ex);
                }
            } catch (final Throwable e) {
                // Ignore exception as we generated it.
            }

            if (errorReceiverProxy.getErrorReceiver() instanceof ErrorStatistics) {
                ((ErrorStatistics) errorReceiverProxy.getErrorReceiver()).checkRecord(-1);
            }

            if (LOGGER.isTraceEnabled() && streamSource.getStream() != null) {
                LOGGER.trace("Error while processing stream task: id = " + streamSource.getStream().getId(), ex);
            }
        } else {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), ex.getMessage(), ex);
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
        private final StreamProcessor streamProcessor;
        private final StreamTask streamTask;

        private OutputStream processInfoOutputStream;
        private StreamTarget processInfoStreamTarget;

        public ProcessInfoOutputStreamProvider(final StreamStore streamStore, final StreamCloser streamCloser,
                                               final MetaData metaData, final Stream stream, final StreamProcessor streamProcessor,
                                               final StreamTask streamTask) {
            this.streamStore = streamStore;
            this.streamCloser = streamCloser;
            this.metaData = metaData;
            this.stream = stream;
            this.streamProcessor = streamProcessor;
            this.streamTask = streamTask;
        }

        @Override
        public Destination borrowDestination() throws IOException {
            return this;
        }

        @Override
        public void returnDestination(final Destination destination) throws IOException {
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return getOutputStream(null, null);
        }

        @Override
        public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
            if (processInfoOutputStream == null) {
                // Create a processing info stream to write all processing
                // information to.
                final Stream errorStream = Stream.createProcessedStream(stream, stream.getFeed(), StreamType.ERROR,
                        streamProcessor, streamTask);

                processInfoStreamTarget = streamStore.openStreamTarget(errorStream);
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
        public List<Processor> createProcessors() {
            return Collections.emptyList();
        }
    }
}
