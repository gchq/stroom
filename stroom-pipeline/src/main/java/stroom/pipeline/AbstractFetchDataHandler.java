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
 */

package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataStatus;
import stroom.data.store.api.CompoundInputStream;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.feed.FeedProperties;
import stroom.pipeline.scope.PipelineScopeRunnable;
import stroom.io.BasicStreamCloser;
import stroom.io.StreamCloser;
import stroom.logging.StreamEventLog;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.reader.BOMRemovalInputStream;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.pipeline.writer.AbstractWriter;
import stroom.pipeline.writer.OutputStreamAppender;
import stroom.pipeline.writer.TextWriter;
import stroom.pipeline.writer.XMLWriter;
import stroom.security.Security;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;

import javax.inject.Provider;
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
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    private static final int STREAM_BUFFER_SIZE = 1024 * 100;

    private final Long streamsLength = 1L;
    private final boolean streamsTotalIsExact = true;

    private final StreamStore streamStore;
    private final FeedProperties feedProperties;
    private final Provider<FeedHolder> feedHolderProvider;
    private final Provider<MetaDataHolder> metaDataHolderProvider;
    private final Provider<PipelineHolder> pipelineHolderProvider;
    private final Provider<StreamHolder> streamHolderProvider;
    private final PipelineStore pipelineStore;
    private final Provider<PipelineFactory> pipelineFactoryProvider;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final PipelineDataCache pipelineDataCache;
    private final StreamEventLog streamEventLog;
    private final Security security;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    private Long streamsOffset = 0L;
    private Long streamsTotal = 0L;
    private Long pageOffset = 0L;
    private Long pageLength = 0L;
    private Long pageTotal = 0L;
    private boolean pageTotalIsExact = false;

    AbstractFetchDataHandler(final StreamStore streamStore,
                             final FeedProperties feedProperties,
                             final Provider<FeedHolder> feedHolderProvider,
                             final Provider<MetaDataHolder> metaDataHolderProvider,
                             final Provider<PipelineHolder> pipelineHolderProvider,
                             final Provider<StreamHolder> streamHolderProvider,
                             final PipelineStore pipelineStore,
                             final Provider<PipelineFactory> pipelineFactoryProvider,
                             final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                             final PipelineDataCache pipelineDataCache,
                             final StreamEventLog streamEventLog,
                             final Security security,
                             final PipelineScopeRunnable pipelineScopeRunnable) {
        this.streamStore = streamStore;
        this.feedProperties = feedProperties;
        this.feedHolderProvider = feedHolderProvider;
        this.metaDataHolderProvider = metaDataHolderProvider;
        this.pipelineHolderProvider = pipelineHolderProvider;
        this.streamHolderProvider = streamHolderProvider;
        this.pipelineStore = pipelineStore;
        this.pipelineFactoryProvider = pipelineFactoryProvider;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.pipelineDataCache = pipelineDataCache;
        this.streamEventLog = streamEventLog;
        this.security = security;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    protected AbstractFetchDataResult getData(final Long streamId,
                                              final String childStreamTypeName,
                                              final OffsetRange<Long> streamsRange,
                                              final OffsetRange<Long> pageRange,
                                              final boolean markerMode,
                                              final DocRef pipeline,
                                              final boolean showAsHtml,
                                              final Severity... expandedSeverities) {
        // Allow users with 'Use' permission to read data, pipelines and XSLT.
        return security.useAsReadResult(() -> {
            final StreamCloser streamCloser = new BasicStreamCloser();
            List<String> availableChildStreamTypes;
            String feedName = null;
            String streamTypeName = null;

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
                streamTypeName = streamSource.getStreamTypeName();

                // Find out which child stream types are available.
                availableChildStreamTypes = getAvailableChildStreamTypes(streamSource);

                // Are we getting and are we able to get the child stream?
                if (childStreamTypeName != null) {
                    final StreamSource childStreamSource = streamSource.getChildStream(childStreamTypeName);

                    // If we got something then change the stream.
                    if (childStreamSource != null) {
                        streamSource = childStreamSource;
                        streamTypeName = childStreamTypeName;
                    }
                    streamCloser.add(streamSource);
                }

                // Get the feed name.
                if (streamSource != null && streamSource.getStream() != null && streamSource.getStream().getFeedName() != null) {
                    feedName = streamSource.getStream().getFeedName();
                }

                // Get the boundary and segment input streams.
                final CompoundInputStream compoundInputStream = streamSource.getCompoundInputStream();
                streamCloser.add(compoundInputStream);

                streamsOffset = streamsRange.getOffset();
                streamsTotal = compoundInputStream.getEntryCount();
                if (streamsOffset >= streamsTotal) {
                    streamsOffset = streamsTotal - 1;
                }

                final SegmentInputStream segmentInputStream = compoundInputStream.getNextInputStream(streamsOffset);
                streamCloser.add(segmentInputStream);

                writeEventLog(streamSource.getStream(), feedName, streamTypeName, null);

                // If this is an error stream and the UI is requesting markers then
                // create a list of markers.
                if (StreamTypeNames.ERROR.equals(streamTypeName) && markerMode) {
                    return createMarkerResult(feedName, streamTypeName, segmentInputStream, pageRange, availableChildStreamTypes, expandedSeverities);
                }

                return createDataResult(feedName, streamTypeName, segmentInputStream, pageRange, availableChildStreamTypes, pipeline, showAsHtml, streamSource);

            } catch (final IOException | RuntimeException e) {
                writeEventLog(streamSource.getStream(), feedName, streamTypeName, e);

                if (DataStatus.LOCKED.equals(streamSource.getStream().getStatus())) {
                    return createErrorResult("You cannot view locked streams.");
                }
                if (DataStatus.DELETED.equals(streamSource.getStream().getStatus())) {
                    return createErrorResult("This data may no longer exist.");
                }

                return createErrorResult(e.getMessage());

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
        });
    }

    private FetchMarkerResult createMarkerResult(final String feedName, final String streamTypeName, final SegmentInputStream segmentInputStream, final OffsetRange<Long> pageRange, final List<String> availableChildStreamTypes, final Severity... expandedSeverities) throws IOException {
        List<Marker> markersList;

        // Get the appropriate encoding for the stream type.
        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

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

        final String classification = feedProperties.getDisplayClassification(feedName);
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(streamsOffset, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(streamsTotal, streamsTotalIsExact);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>((long) pageOffset,
                (long) resultList.size());
        final RowCount<Long> pageRowCount = new RowCount<>((long) markersList.size(), true);

        return new FetchMarkerResult(streamTypeName, classification, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes,
                new SharedList<>(resultList));
    }

    private FetchDataResult createDataResult(final String feedName, final String streamTypeName, final SegmentInputStream segmentInputStream, final OffsetRange<Long> pageRange, final List<String> availableChildStreamTypes, final DocRef pipeline, final boolean showAsHtml, final StreamSource streamSource) throws IOException {
        // Read the input stream into a string.
        // If the input stream has multiple segments then we are going to
        // read it in segment mode.
        String rawData;
        if (segmentInputStream.count() > 1) {
            rawData = getSegmentedData(feedName, streamTypeName, pageRange, segmentInputStream);
        } else {
            rawData = getNonSegmentedData(feedName, streamTypeName, pageRange, segmentInputStream);
        }

        String output;
        // If we have a pipeline then we will try and use it.
        if (pipeline != null) {
            try {
                // If we have a pipeline then use it.
                output = usePipeline(streamSource, rawData, feedName, pipeline);
            } catch (final RuntimeException e) {
                output = e.getMessage();
                if (output == null || output.length() == 0) {
                    output = e.toString();
                }
            }

        } else {
//                    // Try and pretty print XML.
//                    try {
//                        output = XMLUtil.prettyPrintXML(rawData);
//                    } catch (final RuntimeException e) {
//                        // Ignore.
//                    }
//
//            // If we failed to pretty print XML then return raw data.
//            if (output == null) {
            output = rawData;
//            }
        }

        // Set the result.
        final String classification = feedProperties.getDisplayClassification(feedName);
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(streamsOffset, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(streamsTotal, streamsTotalIsExact);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>(pageOffset, pageLength);
        final RowCount<Long> pageRowCount = new RowCount<>(pageTotal, pageTotalIsExact);
        return new FetchDataResult(streamTypeName, classification, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes, output, showAsHtml);
    }

    private FetchDataResult createErrorResult(final String error) {
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(0L, 0L);
        final RowCount<Long> streamsRowCount = new RowCount<>(0L, true);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>(0L, 0L);
        final RowCount<Long> pageRowCount = new RowCount<>(0L, true);
        return new FetchDataResult(StreamTypeNames.RAW_EVENTS, null, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, null, error, false);
    }

    private void writeEventLog(final Data stream, final String feedName, final String streamTypeName,
                               final Exception e) {
        try {
            streamEventLog.viewStream(stream, feedName, streamTypeName, e);
        } catch (final Exception e2) {
            LOGGER.debug(e.getMessage(), e2);
        }
    }

    private String getSegmentedData(final String feedName, final String streamTypeName, final OffsetRange<Long> pageRange,
                                    final SegmentInputStream segmentInputStream) {
        // Get the appropriate encoding for the stream type.
        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

        // Set the page total.
        if (segmentInputStream.count() > 2) {
            // Subtract 2 to account for the XML root elements (header and footer).
            pageTotal = segmentInputStream.count() - 2;
        } else {
            pageTotal = segmentInputStream.count();
        }
        pageTotalIsExact = true;

        // Make sure we can't exceed the page total.
        pageOffset = pageRange.getOffset();
        if (pageOffset >= pageTotal) {
            pageOffset = pageTotal - 1;
        }

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

    private String getNonSegmentedData(final String feedName, final String streamTypeName, final OffsetRange<Long> pageRange,
                                       final SegmentInputStream segmentInputStream) throws IOException {
        // Get the appropriate encoding for the stream type.
        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

        pageOffset = pageRange.getOffset();
        final long minLineNo = pageOffset;
        final long maxLineNo = pageOffset + pageRange.getLength();
        final StringBuilder sb = new StringBuilder();
        long lineNo = 0;
        int len = 0;

        try (BOMRemovalInputStream bomRemovalIS = new BOMRemovalInputStream(segmentInputStream, encoding);
             final Reader reader = new InputStreamReader(bomRemovalIS, encoding)) {
            final char[] buffer = new char[STREAM_BUFFER_SIZE];

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

        // Make sure we can't exceed the page total.
        if (pageOffset >= pageTotal) {
            pageOffset = pageTotal - 1;
        }

        return sb.toString();
    }

    private String usePipeline(final StreamSource streamSource, final String string, final String feedName,
                               final DocRef pipelineRef) {
        return pipelineScopeRunnable.scopeResult(() -> {
            try {
                String data;

                final FeedHolder feedHolder = feedHolderProvider.get();
                final MetaDataHolder metaDataHolder = metaDataHolderProvider.get();
                final PipelineHolder pipelineHolder = pipelineHolderProvider.get();
                final StreamHolder streamHolder = streamHolderProvider.get();
                final PipelineFactory pipelineFactory = pipelineFactoryProvider.get();
                final ErrorReceiverProxy errorReceiverProxy = errorReceiverProxyProvider.get();

                final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
                errorReceiverProxy.setErrorReceiver(errorReceiver);

                // Set the pipeline so it can be used by a filter if needed.
                final PipelineDoc loadedPipeline = pipelineStore.readDocument(pipelineRef);
                if (loadedPipeline == null) {
                    throw new EntityServiceException("Unable to load pipeline");
                }

                feedHolder.setFeedName(feedName);
                // Setup the meta data holder.
                metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, pipelineStore));
                pipelineHolder.setPipeline(DocRefUtil.create(loadedPipeline));
                // Get the stream providers.
                streamHolder.setStream(streamSource.getStream());
                streamHolder.addProvider(streamSource);
                streamHolder.addProvider(streamSource.getChildStream(StreamTypeNames.META));
                streamHolder.addProvider(streamSource.getChildStream(StreamTypeNames.CONTEXT));

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
                final OutputStreamAppender appender = new OutputStreamAppender(errorReceiverProxy, bos);
                writer.setTarget(appender);

                // Process the input.
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(string.getBytes());
                ProcessException processException = null;
                try {
                    pipeline.startProcessing();
                } catch (final RuntimeException e) {
                    processException = new ProcessException(e.getMessage());
                    throw processException;
                } finally {
                    try {
                        pipeline.process(inputStream, StreamUtil.DEFAULT_CHARSET_NAME);
                    } catch (final RuntimeException e) {
                        if (processException != null) {
                            processException.addSuppressed(e);
                        } else {
                            processException = new ProcessException(e.getMessage());
                            throw processException;
                        }
                    } finally {
                        try {
                            pipeline.endProcessing();
                        } catch (final RuntimeException e) {
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
            } catch (final IOException | TransformerException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private List<String> getAvailableChildStreamTypes(final StreamSource streamSource) {
        final List<String> availableChildStreamTypes = new ArrayList<>();
        final StreamSource metaStreamSource = streamSource.getChildStream(StreamTypeNames.META);
        if (metaStreamSource != null) {
            availableChildStreamTypes.add(StreamTypeNames.META);
        }
        final StreamSource contextStreamSource = streamSource.getChildStream(StreamTypeNames.CONTEXT);
        if (contextStreamSource != null) {
            availableChildStreamTypes.add(StreamTypeNames.CONTEXT);
        }
        return availableChildStreamTypes;
    }
}
