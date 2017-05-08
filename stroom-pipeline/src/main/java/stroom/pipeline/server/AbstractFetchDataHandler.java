/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.EntityServiceException;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.io.StreamCloser;
import stroom.logging.StreamEventLog;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.writer.AbstractWriter;
import stroom.pipeline.server.writer.OutputStreamAppender;
import stroom.pipeline.server.writer.TextWriter;
import stroom.pipeline.server.writer.XMLWriter;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.DocRef;
import stroom.resource.server.BOMRemovalInputStream;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.server.fs.serializable.CompoundInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.AbstractTaskHandler;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;

import javax.xml.transform.TransformerException;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFetchDataHandler<A extends FetchDataAction>
        extends AbstractTaskHandler<A, AbstractFetchDataResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFetchDataHandler.class);

    private static final int MAX_LINE_LENGTH = 1000;
    private final Long streamsLength = 1L;
    private final boolean streamsTotalIsExact = true;

    private final StreamStore streamStore;
    private final FeedService feedService;
    private final FeedHolder feedHolder;
    private final PipelineHolder pipelineHolder;
    private final StreamHolder streamHolder;
    private final PipelineEntityService pipelineEntityService;
    private final PipelineFactory pipelineFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineDataCache pipelineDataCache;
    private final StreamEventLog streamEventLog;
    private final SecurityContext securityContext;

    private Long streamsOffset = 0L;
    private Long streamsTotal = 0L;
    private Long pageOffset = 0L;
    private Long pageLength = 0L;
    private Long pageTotal = 0L;
    private boolean pageTotalIsExact = false;

    AbstractFetchDataHandler(final StreamStore streamStore, final FeedService feedService, final FeedHolder feedHolder, final PipelineHolder pipelineHolder, final StreamHolder streamHolder, final PipelineEntityService pipelineEntityService, final PipelineFactory pipelineFactory, final ErrorReceiverProxy errorReceiverProxy, final PipelineDataCache pipelineDataCache, final StreamEventLog streamEventLog, final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.streamHolder = streamHolder;
        this.pipelineEntityService = pipelineEntityService;
        this.pipelineFactory = pipelineFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineDataCache = pipelineDataCache;
        this.streamEventLog = streamEventLog;
        this.securityContext = securityContext;
    }

    protected AbstractFetchDataResult getData(final Long streamId, final StreamType childStreamType,
                                              final OffsetRange<Long> streamsRange, final OffsetRange<Long> pageRange, final boolean markerMode,
                                              final DocRef pipeline, final boolean showAsHtml, final Severity... expandedSeverities) {
        try {
            // Allow users with 'Use' permission to read data, pipelines and XSLT.
            securityContext.elevatePermissions();

            final StreamCloser streamCloser = new StreamCloser();
            List<StreamType> availableChildStreamTypes;
            Feed feed = null;
            StreamType streamType = null;

            StreamSource streamSource = null;
            RuntimeException exception = null;
            try {
                // Get the stream source.
                streamSource = streamStore.openStreamSource(streamId, true);

                // If we have no stream then let the client know it has been
                // deleted.
                if (streamSource == null) {
                    final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(streamsOffset, streamsLength);
                    final RowCount<Long> streamsRowCount = new RowCount<>(streamsTotal, streamsTotalIsExact);
                    final OffsetRange<Long> resultPageRange = new OffsetRange<>(pageOffset, (long) 1);
                    final RowCount<Long> pageRowCount = new RowCount<>((long) 1, true);

                    return new FetchDataResult(null, null, resultStreamsRange,
                            streamsRowCount, resultPageRange, pageRowCount, null,
                            "Stream has been deleted", showAsHtml);
                }

                streamCloser.add(streamSource);
                streamType = streamSource.getType();

                // Find out which child stream types are available.
                availableChildStreamTypes = getAvailableChildStreamTypes(streamSource);

                // Are we getting and are we able to get the child stream?
                if (childStreamType != null) {
                    final StreamSource childStreamSource = streamSource.getChildStream(childStreamType);

                    // If we got something then change the stream.
                    if (childStreamSource != null) {
                        streamSource = childStreamSource;
                        streamType = childStreamType;
                    }
                    streamCloser.add(streamSource);
                }

                // Load the feed.
                feed = feedService.load(streamSource.getStream().getFeed());

                // Get the boundary and segment input streams.
                final CompoundInputStream compoundInputStream = new CompoundInputStream(streamSource);
                streamCloser.add(compoundInputStream);

                streamsOffset = streamsRange.getOffset();
                streamsTotal = compoundInputStream.getEntryCount();
                if (streamsOffset >= streamsTotal) {
                    streamsOffset = streamsTotal - 1;
                }

                final RASegmentInputStream segmentInputStream = compoundInputStream.getNextInputStream(streamsOffset);
                streamCloser.add(segmentInputStream);

                writeEventLog(streamSource.getStream(), feed, streamType, null);

                // If this is an error stream and the UI is requesting markers then
                // create a list of markers.
                if (StreamType.ERROR.equals(streamType) && markerMode) {
                    return createMarkerResult(feed, streamType, segmentInputStream, pageRange, availableChildStreamTypes, expandedSeverities);
                }

                return createDataResult(feed, streamType, segmentInputStream, pageRange, availableChildStreamTypes, pipeline, showAsHtml, streamSource);

            } catch (final IOException e) {
                writeEventLog(streamSource.getStream(), feed, streamType, e);
                exception = new RuntimeException(e);
                throw exception;
            } finally {
                // Close all open streams.
                try {
                    streamCloser.close();
                } catch (final IOException e) {
                    if (exception != null) {
                        exception.addSuppressed(e);
                    } else {
                        exception = new RuntimeException(e);
                        throw exception;
                    }
                } finally {
                    if (streamSource != null) {
                        streamStore.closeStreamSource(streamSource);
                    }
                }
            }
        } finally {
            securityContext.restorePermissions();
        }
    }

    private FetchMarkerResult createMarkerResult(final Feed feed, final StreamType streamType, final RASegmentInputStream segmentInputStream, final OffsetRange<Long> pageRange, final List<StreamType> availableChildStreamTypes, final Severity... expandedSeverities) throws IOException {
        List<Marker> markersList;

        // Get the appropriate encoding for the stream type.
        final String encoding = EncodingSelection.select(feed, streamType);

        // Include all segments.
        segmentInputStream.includeAll();

        // Get the data from the stream.
        final Reader reader = new InputStreamReader(segmentInputStream, Charset.forName(encoding));

        // Combine markers into a list.
        markersList = new MarkerListCreator().createFullList(reader, expandedSeverities);

        // Create a list just for the request.
        int pageOffset = pageRange.getOffset().intValue();
        if (pageOffset >= markersList.size()) {
            pageOffset = markersList.size() - 1;
        }
        final int max = pageOffset + pageRange.getLength().intValue();
        final int totalResults = markersList.size();
        final List<Marker> resultList = new ArrayList<>();
        for (int i = pageOffset; i < max && i < totalResults; i++) {
            resultList.add(markersList.get(i));
        }

        final String classification = feedService.getDisplayClassification(feed);
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(streamsOffset, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(streamsTotal, streamsTotalIsExact);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>((long) pageOffset,
                (long) resultList.size());
        final RowCount<Long> pageRowCount = new RowCount<>((long) markersList.size(), true);

        return new FetchMarkerResult(streamType, classification, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes,
                new SharedList<>(resultList));
    }

    private FetchDataResult createDataResult(final Feed feed, final StreamType streamType, final RASegmentInputStream segmentInputStream, final OffsetRange<Long> pageRange, final List<StreamType> availableChildStreamTypes, final DocRef pipeline, final boolean showAsHtml, final StreamSource streamSource) throws IOException {
        // Read the input stream into a string.
        // If the input stream has multiple segments then we are going to
        // read it in segment mode.
        String rawData;
        if (segmentInputStream.count() > 1) {
            rawData = getSegmentedData(feed, streamType, pageRange, segmentInputStream);
        } else {
            rawData = getNonSegmentedData(feed, streamType, pageRange, segmentInputStream);
        }

        String output;
        // If we have a pipeline then we will try and use it.
        if (pipeline != null) {
            try {
                // If we have a pipeline then use it.
                output = usePipeline(streamSource, rawData, feed, pipeline);
            } catch (final Exception e) {
                output = e.getMessage();
                if (output == null || output.length() == 0) {
                    output = e.toString();
                }
            }

        } else {
//                    // Try and pretty print XML.
//                    try {
//                        output = XMLUtil.prettyPrintXML(rawData);
//                    } catch (final Exception ex) {
//                        // Ignore.
//                    }
//
//            // If we failed to pretty print XML then return raw data.
//            if (output == null) {
            output = rawData;
//            }
        }

        // Make sure we can't exceed the page total.
        if (pageOffset > pageTotal) {
            pageOffset = pageTotal;
        }

        // Set the result.
        final String classification = feedService.getDisplayClassification(feed);
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(streamsOffset, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(streamsTotal, streamsTotalIsExact);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>(pageOffset, pageLength);
        final RowCount<Long> pageRowCount = new RowCount<>(pageTotal, pageTotalIsExact);
        return new FetchDataResult(streamType, classification, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes, output, showAsHtml);
    }

    private void writeEventLog(final Stream stream, final Feed feed, final StreamType streamType,
                               final Exception e) {
        try {
            streamEventLog.viewStream(stream, feed, streamType, e);
        } catch (final Exception ex) {
            LOGGER.debug(ex.getMessage(), ex);
        }
    }

    private String getSegmentedData(final Feed feed, final StreamType streamType, final OffsetRange<Long> pageRange,
                                    final RASegmentInputStream segmentInputStream) throws IOException {
        // Get the appropriate encoding for the stream type.
        final String encoding = EncodingSelection.select(feed, streamType);

        pageOffset = pageRange.getOffset();

        // Set the page total.
        if (segmentInputStream.count() > 2) {
            // Subtract 2 to account for the XML root elements.
            pageTotal = segmentInputStream.count() - 2;
        }
        pageTotalIsExact = true;

        // Include start root element.
        segmentInputStream.include(0);
        // Add the requested records.
        for (long i = pageOffset + 1; pageLength < pageRange.getLength() && i <= segmentInputStream.count() - 2; i++) {
            segmentInputStream.include(i);
            pageLength++;
        }
        // Include end root element.
        segmentInputStream.include(segmentInputStream.count() - 1);

        // Get the data from the stream.
        return StreamUtil.streamToString(segmentInputStream, Charset.forName(encoding));
    }

    private String getNonSegmentedData(final Feed feed, final StreamType streamType, final OffsetRange<Long> pageRange,
                                       final RASegmentInputStream segmentInputStream) throws IOException {
        // Get the appropriate encoding for the stream type.
        final String encoding = EncodingSelection.select(feed, streamType);

        pageOffset = pageRange.getOffset();
        final long minLineNo = pageOffset;
        final long maxLineNo = pageOffset + pageRange.getLength();
        final StringBuilder sb = new StringBuilder();
        long lineNo = 0;
        int len = 0;

        try (BOMRemovalInputStream bomRemovalIS = new BOMRemovalInputStream(segmentInputStream, encoding);
             final Reader reader = new InputStreamReader(bomRemovalIS, encoding)) {
            final char[] buffer = new char[FileSystemUtil.STREAM_BUFFER_SIZE];

            final long maxLength = MAX_LINE_LENGTH * pageRange.getLength();
            while (lineNo < maxLineNo && (len = reader.read(buffer)) != -1) {
                for (int i = 0; i < len && sb.length() < maxLength && lineNo < maxLineNo; i++) {
                    final char c = buffer[i];
                    if (lineNo >= minLineNo) {
                        sb.append(c);

                        if (c == '\n') {
                            lineNo++;
                            pageLength++;
                        }
                    } else if (c == '\n') {
                        // Increment the line number for all natural line breaks.
                        lineNo++;
                    }
                }
            }
        }

        // Increment the line count by one more if we have some content.
        if (lineNo < maxLineNo && sb.length() > 0) {
            pageLength++;
        }

        pageTotal = pageOffset + pageLength;
        // If there was no more content then the page total has been reached.
        pageTotalIsExact = len == -1;

        return sb.toString();
    }

    private String usePipeline(final StreamSource streamSource, final String string, final Feed feed,
                               final DocRef pipelineEntity) throws IOException, TransformerException {
        String data;

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(errorReceiver);

        // Set the pipeline so it can be used by a filter if needed.
        final PipelineEntity loadedPipeline = pipelineEntityService.loadByUuid(pipelineEntity.getUuid());
        if (loadedPipeline == null) {
            throw new EntityServiceException("Unable to load pipeline");
        }

        feedHolder.setFeed(feed);
        pipelineHolder.setPipeline(loadedPipeline);
        // Get the stream providers.
        streamHolder.setStream(streamSource.getStream());
        streamHolder.addProvider(streamSource);
        streamHolder.addProvider(streamSource.getChildStream(StreamType.META));
        streamHolder.addProvider(streamSource.getChildStream(StreamType.CONTEXT));

        final PipelineData pipelineData = pipelineDataCache.get(loadedPipeline);
        if (pipelineData == null) {
            throw new EntityServiceException("Pipeline has no data");
        }
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        // Try and find the writer on this pipeline.
        AbstractWriter writer = null;
        final List<XMLWriter> xmlWriters = pipeline.findFilters(XMLWriter.class);
        if (xmlWriters != null && xmlWriters.size() > 0) {
            writer = xmlWriters.get(0);
        } else {
            final List<TextWriter> textWriters = pipeline.findFilters(TextWriter.class);
            if (textWriters != null && textWriters.size() > 0) {
                writer = textWriters.get(0);
            }
        }
        if (writer == null) {
            throw new ProcessException("Pipeline has no writer");
        }

        // Create an output stream and give it to the writer.
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final BufferedOutputStream bos = new BufferedOutputStream(baos);
        final OutputStreamAppender appender = new OutputStreamAppender(bos);
        writer.setTarget(appender);

        // Process the input.
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(string.getBytes());
        ProcessException processException = null;
        try {
            pipeline.startProcessing();
        } catch (final Exception e) {
            processException = new ProcessException(e.getMessage());
            throw processException;
        } finally {
            try {
                pipeline.process(inputStream, StreamUtil.DEFAULT_CHARSET_NAME);
            } catch (final Exception e) {
                if (processException != null) {
                    processException.addSuppressed(e);
                } else {
                    processException = new ProcessException(e.getMessage());
                    throw processException;
                }
            } finally {
                try {
                    pipeline.endProcessing();
                } catch (final Exception e) {
                    if (processException != null) {
                        processException.addSuppressed(e);
                    } else {
                        processException = new ProcessException(e.getMessage());
                        throw processException;
                    }
                } finally {
                    bos.flush();
                    bos.close();
                }
            }
        }

        data = baos.toString(StreamUtil.DEFAULT_CHARSET_NAME);

        if (!errorReceiver.isAllOk()) {
            throw new TransformerException(errorReceiver.toString());
        }

        return data;
    }

    private List<StreamType> getAvailableChildStreamTypes(final StreamSource streamSource) {
        final List<StreamType> availableChildStreamTypes = new ArrayList<>();
        final StreamSource metaStreamSource = streamSource.getChildStream(StreamType.META);
        if (metaStreamSource != null) {
            availableChildStreamTypes.add(StreamType.META);
        }
        final StreamSource contextStreamSource = streamSource.getChildStream(StreamType.CONTEXT);
        if (contextStreamSource != null) {
            availableChildStreamTypes.add(StreamType.CONTEXT);
        }
        return availableChildStreamTypes;
    }
}
