/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.data.store.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.util.shared.EntityServiceException;
import stroom.feed.api.FeedProperties;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.MarkerListCreator;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.reader.BOMRemovalInputStream;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.pipeline.writer.AbstractWriter;
import stroom.pipeline.writer.OutputStreamAppender;
import stroom.pipeline.writer.TextWriter;
import stroom.pipeline.writer.XMLWriter;
import stroom.security.api.Security;
import stroom.data.shared.StreamTypeNames;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class DataFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataFetcher.class);

    private static final int MAX_LINE_LENGTH = 1000;
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    private static final int STREAM_BUFFER_SIZE = 1024 * 100;

    private final Long streamsLength = 1L;
    private final boolean streamsTotalIsExact = true;

    private final Store streamStore;
    private final FeedProperties feedProperties;
    private final Provider<FeedHolder> feedHolderProvider;
    private final Provider<MetaDataHolder> metaDataHolderProvider;
    private final Provider<PipelineHolder> pipelineHolderProvider;
    private final Provider<MetaHolder> metaHolderProvider;
    private final PipelineStore pipelineStore;
    private final Provider<PipelineFactory> pipelineFactoryProvider;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final PipelineDataCache pipelineDataCache;
    private final StreamEventLog streamEventLog;
    private final Security security;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    private Long index = 0L;
    private Long count = 0L;
    private Long pageOffset = 0L;
    private Long pageLength = 0L;
    private Long pageTotal = 0L;
    private boolean pageTotalIsExact = false;

    DataFetcher(final Store streamStore,
                final FeedProperties feedProperties,
                final Provider<FeedHolder> feedHolderProvider,
                final Provider<MetaDataHolder> metaDataHolderProvider,
                final Provider<PipelineHolder> pipelineHolderProvider,
                final Provider<MetaHolder> metaHolderProvider,
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
        this.metaHolderProvider = metaHolderProvider;
        this.pipelineStore = pipelineStore;
        this.pipelineFactoryProvider = pipelineFactoryProvider;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.pipelineDataCache = pipelineDataCache;
        this.streamEventLog = streamEventLog;
        this.security = security;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    public void reset() {
        index = 0L;
        count = 0L;
        pageOffset = 0L;
        pageLength = 0L;
        pageTotal = 0L;
        pageTotalIsExact = false;
    }

    public AbstractFetchDataResult getData(final Long streamId,
                                           final String childStreamTypeName,
                                           final OffsetRange<Long> streamsRange,
                                           final OffsetRange<Long> pageRange,
                                           final boolean markerMode,
                                           final DocRef pipeline,
                                           final boolean showAsHtml,
                                           final Severity... expandedSeverities) {
        // Allow users with 'Use' permission to read data, pipelines and XSLT.
        return security.useAsReadResult(() -> {
            List<String> availableChildStreamTypes;
            String feedName = null;
            String streamTypeName = null;
            String eventId = String.valueOf(streamId);
            Meta meta = null;

            // Get the stream source.
            try (final Source source = streamStore.openSource(streamId, true)) {
                // If we have no stream then let the client know it has been
                // deleted.
                if (source == null) {
                    final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(index, streamsLength);
                    final RowCount<Long> streamsRowCount = new RowCount<>(count, streamsTotalIsExact);
                    final OffsetRange<Long> resultPageRange = new OffsetRange<>(pageOffset, (long) 1);
                    final RowCount<Long> pageRowCount = new RowCount<>((long) 1, true);

                    writeEventLog(eventId, feedName, streamTypeName, pipeline, new IOException("Stream has been deleted"));

                    return new FetchDataResult(null, null, resultStreamsRange,
                            streamsRowCount, resultPageRange, pageRowCount, null,
                            "Stream has been deleted", showAsHtml);
                }

                meta = source.getMeta();
                feedName = meta.getFeedName();
                streamTypeName = meta.getTypeName();

                // Are we getting and are we able to get the child stream?
                if (childStreamTypeName != null) {
//                    final Source childStreamSource = streamSource.getChildStream(childStreamTypeName);
//
//                    // If we got something then change the stream.
//                    if (childStreamSource != null) {
//                        streamSource = childStreamSource;
                        streamTypeName = childStreamTypeName;
//                    }
//                    streamCloser.add(streamSource);
                }


//                // Get the boundary and segment input streams.
//                final CompoundInputStream compoundInputStream = streamSource.getCompoundInputStream();
//                streamCloser.add(compoundInputStream);

                index = streamsRange.getOffset();
                count = source.count();
                if (index >= count) {
                    index = count - 1;
                }

                try (final InputStreamProvider inputStreamProvider = source.get(index)) {
                    // Find out which child stream types are available.
                    availableChildStreamTypes = getAvailableChildStreamTypes(inputStreamProvider);

                    try (final SegmentInputStream segmentInputStream = inputStreamProvider.get(childStreamTypeName)) {
                        // Get the event id.
                        eventId = String.valueOf(meta.getId());
                        if (count > 1) {
                            eventId += ":" + index;
                        }
                        if (pageRange != null && pageRange.getLength() != null && pageRange.getLength() == 1) {
                            eventId += ":" + (pageRange.getOffset() + 1);
                        }

                        writeEventLog(eventId, feedName, streamTypeName, pipeline, null);

                        // If this is an error stream and the UI is requesting markers then
                        // create a list of markers.
                        if (StreamTypeNames.ERROR.equals(streamTypeName) && markerMode) {
                            return createMarkerResult(feedName, streamTypeName, segmentInputStream, pageRange, availableChildStreamTypes, expandedSeverities);
                        }

                        return createDataResult(feedName, streamTypeName, segmentInputStream, pageRange, availableChildStreamTypes, pipeline, showAsHtml, source, inputStreamProvider);
                    }
                }

            } catch (final IOException | RuntimeException e) {
                writeEventLog(eventId, feedName, streamTypeName, pipeline, e);

                if (meta != null) {
                    if (Status.LOCKED.equals(meta.getStatus())) {
                        return createErrorResult("You cannot view locked streams.");
                    }
                    if (Status.DELETED.equals(meta.getStatus())) {
                        return createErrorResult("This data may no longer exist.");
                    }
                }

                return createErrorResult(e.getMessage());
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
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(index, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(count, streamsTotalIsExact);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>((long) pageOffset,
                (long) resultList.size());
        final RowCount<Long> pageRowCount = new RowCount<>((long) markersList.size(), true);

        return new FetchMarkerResult(streamTypeName, classification, resultStreamsRange,
                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes,
                new SharedList<>(resultList));
    }

    private FetchDataResult createDataResult(final String feedName, final String streamTypeName, final SegmentInputStream segmentInputStream, final OffsetRange<Long> pageRange, final List<String> availableChildStreamTypes, final DocRef pipeline, final boolean showAsHtml, final Source streamSource, final InputStreamProvider inputStreamProvider) throws IOException {
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
                output = usePipeline(streamSource, rawData, feedName, pipeline, inputStreamProvider);
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
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(index, streamsLength);
        final RowCount<Long> streamsRowCount = new RowCount<>(count, streamsTotalIsExact);
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

    private void writeEventLog(final String eventId,
                               final String feedName,
                               final String streamTypeName,
                               final DocRef pipelineRef,
                               final Exception e) {
        try {
            streamEventLog.viewStream(eventId, feedName, streamTypeName, pipelineRef, e);
        } catch (final Exception ex) {
            LOGGER.debug(ex.getMessage(), ex);
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

    private String usePipeline(final Source streamSource, final String string, final String feedName,
                               final DocRef pipelineRef, final InputStreamProvider inputStreamProvider) {
        return pipelineScopeRunnable.scopeResult(() -> {
            try {
                String data;

                final FeedHolder feedHolder = feedHolderProvider.get();
                final MetaDataHolder metaDataHolder = metaDataHolderProvider.get();
                final PipelineHolder pipelineHolder = pipelineHolderProvider.get();
                final MetaHolder metaHolder = metaHolderProvider.get();
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
                metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));
                pipelineHolder.setPipeline(DocRefUtil.create(loadedPipeline));
                // Get the stream providers.
                metaHolder.setMeta(streamSource.getMeta());
                metaHolder.setInputStreamProvider(inputStreamProvider);

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

    private List<String> getAvailableChildStreamTypes(final InputStreamProvider inputStreamProvider) throws IOException {
        final List<String> availableChildStreamTypes = new ArrayList<>();
        try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.META)) {
            if (inputStream != null) {
                availableChildStreamTypes.add(StreamTypeNames.META);
            }
        }
        try (final InputStream inputStream = inputStreamProvider.get(StreamTypeNames.CONTEXT)) {
            if (inputStream != null) {
                availableChildStreamTypes.add(StreamTypeNames.CONTEXT);
            }
        }
        return availableChildStreamTypes;
    }
}
