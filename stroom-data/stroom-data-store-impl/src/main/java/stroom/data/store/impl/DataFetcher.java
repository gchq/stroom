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

import stroom.data.shared.DataRange;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
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
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SourceLocation;
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
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.Summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String TRUNCATED_SUFFIX = "...[TRUNCATED IN USER INTERFACE]";
//    private static final int MAX_LINE_LENGTH = 1000;
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    private static final int STREAM_BUFFER_SIZE = 1024 * 100;

    // Max chars we can return
    private static final long MAX_CHARS = 10_000L; // TODO get from config
    private static final long MAX_ERRORS_ON_PAGE = 500L; // TODO get from config

    private final Long partsToReturn = 1L;
    private final Long segmentsToReturn = 1L;

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
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    //    private Long index = 0L;
    private Long partCount = 0L;
//    private Long pageOffset = 0L;
//    private Long pageLength = 0L;

    // This is either the number of segments or the number of lines
    private Long pageTotal = 0L;
//    private boolean pageTotalIsExact = false;

    private Long segmentNumber;

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
                final SecurityContext securityContext,
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
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    //    public void reset() {
//        index = 0L;
//        count = 0L;
//        pageOffset = 0L;
//        pageLength = 0L;
//        pageTotal = 0L;
//        pageTotalIsExact = false;
//    }
    public AbstractFetchDataResult getData(final FetchDataRequest fetchDataRequest) {
//
//    }
//
//    public AbstractFetchDataResult getData(final long streamId,
//                                           final String childStreamTypeName,
//                                           final OffsetRange<Long> streamsRange,
//                                           final OffsetRange<Long> pageRange,
//                                           final boolean markerMode,
//                                           final DocRef pipeline,
//                                           final boolean showAsHtml,
//                                           final Severity... expandedSeverities) {
        // Allow users with 'Use' permission to read data, pipelines and XSLT.
        return securityContext.useAsReadResult(() -> {
            List<String> availableChildStreamTypes;
            String feedName = null;
            String streamTypeName = null;
            String eventId = String.valueOf(fetchDataRequest.getSourceLocation().getId());
            Meta meta = null;
            final SourceLocation sourceLocation = fetchDataRequest.getSourceLocation();
            final DataRange dataRange = sourceLocation.getDataRange();

            // Get the stream source.
            try (final Source source = streamStore.openSource(fetchDataRequest.getSourceLocation().getId(), true)) {
                // If we have no stream then let the client know it has been
                // deleted.
                if (source == null) {
                    final String msg = "Stream has been deleted";
                    final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(
                            sourceLocation.getPartNo(), partsToReturn);
                    final RowCount<Long> totalItemCount = new RowCount<>(0L, true);
                    final OffsetRange<Long> itemRange = new OffsetRange<>(0L, (long) 1);
                    final RowCount<Long> totalCharCount = new RowCount<>((long) msg.length(), true);

                    writeEventLog(
                            eventId,
                            feedName,
                            streamTypeName,
                            fetchDataRequest.getPipeline(),
                            new IOException(msg));

//                    return new FetchDataResult(null, null, resultStreamsRange,
//                            streamsRowCount, resultPageRange, pageRowCount, null,
//                            "Stream has been deleted", fetchDataRequest.isShowAsHtml());
                    return new FetchDataResult(
                            null,
                            null,
                            sourceLocation,
                            itemRange,
                            totalItemCount,
                            totalCharCount,
                            null,
                            msg,
                            fetchDataRequest.isShowAsHtml(),
                            null);  // Don't really know segmented state as stream is gone
                }

                meta = source.getMeta();
                feedName = meta.getFeedName();
                streamTypeName = meta.getTypeName();
                // See if a specific child stream type was requested
                streamTypeName = sourceLocation.getOptChildType()
                        .orElse(meta.getTypeName());

//                // Are we getting and are we able to get the child stream?
//                if (fetchDataRequest.getDataRange().getChildStreamType(). != null) {
////                    final Source childStreamSource = streamSource.getChildStream(childStreamTypeName);
////
////                    // If we got something then change the stream.
////                    if (childStreamSource != null) {
////                        streamSource = childStreamSource;
//                    streamTypeName = fetchDataRequest.getChildStreamType();
////                    }
////                    streamCloser.add(streamSource);
//                }


//                // Get the boundary and segment input streams.
//                final CompoundInputStream compoundInputStream = streamSource.getCompoundInputStream();
//                streamCloser.add(compoundInputStream);

//                index = fetchDataRequest.getStreamRange().getOffset();
                long partNo = fetchDataRequest.getSourceLocation().getPartNo();
                partCount = source.count();

                // Prevent user going past last part
                if (partNo >= partCount) {
                    partNo = partCount - 1;
                }

                try (final InputStreamProvider inputStreamProvider = source.get(partNo)) {
                    // Find out which child stream types are available.
                    availableChildStreamTypes = getAvailableChildStreamTypes(inputStreamProvider);

                    String requestedChildStreamType = sourceLocation.getOptChildType().orElse(null);
                    try (final SegmentInputStream segmentInputStream = inputStreamProvider.get(requestedChildStreamType)) {
                        // Get the event id.
                        eventId = String.valueOf(meta.getId());
                        if (partCount > 1) {
                            eventId += ":" + partNo;
                        }
//                        final OffsetRange<Long> pageRange = fetchDataRequest.getDataRange().getSegmentNumber();
                        if (sourceLocation.getOptSegmentNo().isPresent()) {
                            eventId = ":" + sourceLocation.getOptSegmentNo().getAsLong();
                        }

//                        if (pageRange != null && pageRange.getLength() != null && pageRange.getLength() == 1) {
//                            eventId += ":" + (pageRange.getOffset() + 1);
//                        }

                        writeEventLog(eventId, feedName, streamTypeName, fetchDataRequest.getPipeline(), null);

                        // If this is an error stream and the UI is requesting markers then
                        // create a list of markers.
                        if (StreamTypeNames.ERROR.equals(streamTypeName) && fetchDataRequest.isMarkerMode()) {
                            return createMarkerResult(
                                    feedName,
                                    streamTypeName,
                                    segmentInputStream,
                                    fetchDataRequest.getSourceLocation(),
                                    availableChildStreamTypes,
                                    fetchDataRequest.getExpandedSeverities());
                        }

                        return createDataResult(
                                feedName,
                                streamTypeName,
                                segmentInputStream,
//                                pageRange,
                                availableChildStreamTypes,
//                                fetchDataRequest.getPipeline(),
//                                fetchDataRequest.isShowAsHtml(),
                                source,
                                inputStreamProvider,
                                fetchDataRequest);
                    }
                }

            } catch (final IOException | RuntimeException e) {
                writeEventLog(eventId, feedName, streamTypeName, fetchDataRequest.getPipeline(), e);

                if (meta != null) {
                    if (Status.LOCKED.equals(meta.getStatus())) {
                        return createErrorResult(sourceLocation, "You cannot view locked streams.");
                    }
                    if (Status.DELETED.equals(meta.getStatus())) {
                        return createErrorResult(sourceLocation, "This data may no longer exist.");
                    }
                }

                return createErrorResult(sourceLocation, e.getMessage());
            }
        });
    }

    private FetchMarkerResult createMarkerResult(final String feedName,
                                                 final String streamTypeName,
                                                 final SegmentInputStream segmentInputStream,
                                                 final SourceLocation sourceLocation,
                                                 final List<String> availableChildStreamTypes,
                                                 final Severity... expandedSeverities) throws IOException {
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
//        int pageOffset = pageRange.getOffset().intValue();
        long pageOffset = sourceLocation.getOptSegmentNo().orElse(0);
        if (pageOffset >= markersList.size()) {
            pageOffset = markersList.size() - 1;
        }
        final long max = pageOffset + MAX_ERRORS_ON_PAGE;
//        final long max = pageOffset + 1;  // TODO do we only want one error at a time?
        final long totalResults = markersList.size();
        final long nonSummaryResults = markersList.stream()
                .filter(marker -> !(marker instanceof Summary))
                .count();
        final List<Marker> resultList = new ArrayList<>();
        for (long i = pageOffset; i < max && i < totalResults; i++) {
            resultList.add(markersList.get((int) i));
        }

        final String classification = feedProperties.getDisplayClassification(feedName);
        final OffsetRange<Long> itemRange = new OffsetRange<>(sourceLocation.getSegmentNo(), (long) resultList.size());
//        final RowCount<Long> streamsRowCount = new RowCount<>(partCount, true);
        final RowCount<Long> totalItemCount = new RowCount<>((long) totalResults, true);
//        final OffsetRange<Long> resultPageRange = new OffsetRange<>((long) pageOffset,
//                (long) resultList.size());
        final RowCount<Long> totalCharCount = new RowCount<>(0L, true);

//        return new FetchMarkerResult(streamTypeName, classification, resultStreamsRange,
//                streamsRowCount, resultPageRange, pageRowCount, availableChildStreamTypes,
//                new ArrayList<>(resultList));
        return new FetchMarkerResult(
                streamTypeName,
                classification,
                sourceLocation,
                itemRange,
                totalItemCount,
                totalCharCount,
                availableChildStreamTypes,
                new ArrayList<>(resultList));
    }

    private FetchDataResult createDataResult(final String feedName,
                                             final String streamTypeName,
                                             final SegmentInputStream segmentInputStream,
//                                             final OffsetRange<Long> pageRange,
                                             final List<String> availableChildStreamTypes,
//                                             final DocRef pipeline,
//                                             final boolean showAsHtml,
                                             final Source streamSource,
                                             final InputStreamProvider inputStreamProvider,
                                             final FetchDataRequest fetchDataRequest) throws IOException {
        final SourceLocation sourceLocation = fetchDataRequest.getSourceLocation();
        // Read the input stream into a string.
        // If the input stream has multiple segments then we are going to
        // read it in segment mode.
        RawResult rawResult;
        DataType dataType = segmentInputStream.count() > 1
                ? DataType.SEGMENTED
                : DataType.NON_SEGMENTED;

        // Get the appropriate encoding for the stream type.
        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

        if (DataType.SEGMENTED.equals(dataType)) {
            rawResult = getSegmentedData(sourceLocation, segmentInputStream, encoding);
        } else {
            // Non-segmented data
            rawResult = getNonSegmentedData(sourceLocation, segmentInputStream, encoding);
        }

        String output;
        final DocRef pipeline = fetchDataRequest.getPipeline();
        // If we have a pipeline then we will try and use it.
        if (pipeline != null) {
            try {
                // If we have a pipeline then use it.
                output = usePipeline(
                        streamSource,
                        rawResult.getRawData(),
                        feedName,
                        pipeline,
                        inputStreamProvider);

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
            output = rawResult.getRawData();
//            }
        }

        // Set the result.
        final String classification = feedProperties.getDisplayClassification(feedName);
//        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(dataRange.getPartNo(), partsToReturn);
        final RowCount<Long> streamsRowCount = new RowCount<>(partCount, true);
//        final OffsetRange<Long> resultPageRange = new OffsetRange<>(pageOffset, pageLength);
//        final RowCount<Long> pageRowCount;
//        final OffsetRange<Long> resultPageRange;
//        if (DataType.SEGMENTED.equals(dataType)) {
//                pageRowCount = new RowCount<>(rawResult.getSegmentsInPartCount(), true);
//                // Always one segment per result
//                resultPageRange = OffsetRange.of(
//                        rawResult.getSourceLocation().getSegmentNo(),
//                        1L);
//
//        } else {
////            long charsInResult = rawResult.getSourceLocation().getOptDataRange()
////                    .map(DataRange::getOptLength)
////                    .filter(OptionalLong::isPresent)
////                    .map(OptionalLong::getAsLong)
////                    .orElse(0L);
//
//            // TODO @AT can we get a total count of all chars available
//            // Make it one bigger than what we currently know
////            long charTotal = rawResult.getSourceLocation().getDataRange().getCharOffsetFrom() +
////                    rawResult.getSourceLocation().getDataRange().getLength() + 1;
//            pageRowCount = new RowCount<>(null, false);
//
//            resultPageRange = OffsetRange.of(
//                    rawResult.getSourceLocation().getDataRange().getCharOffsetFrom(),
//                    rawResult.getSourceLocation().getDataRange().getLength());
//        }

        return new FetchDataResult(
                streamTypeName,
                classification,
                rawResult.getSourceLocation(),
//                resultStreamsRange,
                rawResult.getItemRange(),
                rawResult.getTotalItemCount(),
                rawResult.getTotalCharacterCount(),
                availableChildStreamTypes,
                output,
                fetchDataRequest.isShowAsHtml(),
                dataType);
    }

    private FetchDataResult createErrorResult(final SourceLocation sourceLocation, final String error) {
        final OffsetRange<Long> resultStreamsRange = new OffsetRange<>(0L, 0L);
        final RowCount<Long> streamsRowCount = new RowCount<>(0L, true);
        final OffsetRange<Long> resultPageRange = new OffsetRange<>(0L, 0L);
        final RowCount<Long> pageRowCount = new RowCount<>(0L, true);

//        return new FetchDataResult(StreamTypeNames.RAW_EVENTS, null, resultStreamsRange,
//                streamsRowCount, resultPageRange, pageRowCount, null, error, false);
        return new FetchDataResult(
                StreamTypeNames.RAW_EVENTS,
                null,
                sourceLocation,
                OffsetRange.of(0L, 0L),
                RowCount.of(0L, true),
                RowCount.of(0L, true),
                null,
                error,
                false,
                null);
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

    private RawResult getSegmentedData(final SourceLocation sourceLocation,
                                       final SegmentInputStream segmentInputStream,
                                       final String encoding) throws IOException {
//        // Get the appropriate encoding for the stream type.
//        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

        // Set the page total.
        if (segmentInputStream.count() > 2) {
            // Subtract 2 to account for the XML root elements (header and footer).
            pageTotal = segmentInputStream.count() - 2;
        } else {
            pageTotal = segmentInputStream.count();
        }
//        pageTotalIsExact = true;

        // Make sure we can't exceed the page total.
        segmentNumber = sourceLocation.getOptSegmentNo()
                .orElse(0);

        if (segmentNumber >= pageTotal) {
            segmentNumber = pageTotal - 1;
        }

        // Include start root element.
        segmentInputStream.include(0);

        // Include the requested segment, add one to allow for start root elm
        segmentInputStream.include(segmentNumber + 1);

//        // Add the requested records.
//        for (long i = segmentNumber + 1; pageLength < 1
//                && i <= segmentInputStream.count() - 2; i++) {
//
//            segmentInputStream.include(i);
//            pageLength++;
//        }
        // Include end root element.
        segmentInputStream.include(segmentInputStream.count() - 1);

        // Get the data from the stream.
//        return StreamUtil.streamToString(segmentInputStream, Charset.forName(encoding));
        RawResult rawResult = extractDataRange(sourceLocation, segmentInputStream, encoding);

        // Override the page items range/total as we are dealing in segments/records
        rawResult.setItemRange(OffsetRange.of(segmentNumber, 1L));
        rawResult.setTotalItemCount(RowCount.of(segmentInputStream.count() - 2, true));
        return rawResult;
    }

    private RawResult getNonSegmentedData(final SourceLocation sourceLocation,
                                          final SegmentInputStream segmentInputStream,
                                          final String encoding) throws IOException {

//        final String encoding = feedProperties.getEncoding(feedName, streamTypeName);

        try (BOMRemovalInputStream bomRemovalIS = new BOMRemovalInputStream(segmentInputStream, encoding)) {
            final RawResult rawResult = extractDataRange(sourceLocation, bomRemovalIS, encoding);
            // Non-segmented data exists within parts so set the item info
            rawResult.setItemRange(OffsetRange.of(sourceLocation.getPartNo(), 1L));
            rawResult.setTotalItemCount(RowCount.of(partCount, true));
            return rawResult;
        }
    }

    private RawResult extractDataRange(final SourceLocation sourceLocation,
                                       final InputStream inputStream,
                                       final String encoding) throws IOException {
        // We could have:
        // One potentially VERY long line, too big to display
        // Lots of small lines
        // Multiple long lines that are too big to display
        final StringBuilder strBuilderRange = new StringBuilder();

        // Trackers for what we have so far and where we are
        int currLineNo = 1; // one based
        int currColNo = 1; // one based
        long currCharOffset = 0; //zero based

        int lastLineNo = -1; // one based
        int lastColNo = -1; // one based
        long startCharOffset = -1;
        int startLineNo = -1;
        int startColNo = -1;
        long charsInRangeCount = 0;

        int currBufferLen = 0;
        boolean isFirstChar = true;

        try (final Reader reader = new InputStreamReader(inputStream, encoding)) {
            final char[] buffer = new char[STREAM_BUFFER_SIZE];

            final NonSegmentedIncludeCharPredicate inclusiveFromPredicate = buildInclusiveFromPredicate(
                    sourceLocation.getDataRange());
            final NonSegmentedIncludeCharPredicate exclusiveToPredicate = buildExclusiveToPredicate(
                    sourceLocation.getDataRange());

            // We could jump to the requested offset, but if we do, we can't
            // track the line/col info for the requested range, i.e.
            // to show the right line numbers in the editor. Thus we need
            // to advance through char by char

            boolean reachedEndOfRange = false;
            boolean continueToLineEnd = false;

            while (!reachedEndOfRange && (currBufferLen = reader.read(buffer)) != -1) {

                for (int i = 0; i < currBufferLen; i++) {
                    final char c = buffer[i];

                    if (inclusiveFromPredicate.test(currLineNo, currColNo, currCharOffset, charsInRangeCount)) {
                        // On or after the first requested char

                        boolean isCharAfterRequestedRange = exclusiveToPredicate.test(
                                currLineNo, currColNo, currCharOffset, charsInRangeCount);

                        if (isCharAfterRequestedRange && (!continueToLineEnd || c == '\n') ) {
                            // This is the char after our requested range
                            // or requested range continued to the end of the line
                            // or we have blown the max chars limit
                            reachedEndOfRange = true;
                            break;
                        } else {
                            // Inside the requested range
                            charsInRangeCount++;
                            // Record the start position for the requested range
                            if (startCharOffset == -1) {
                                startCharOffset = currCharOffset;
                                startLineNo = currLineNo;
                                startColNo = currColNo;
                            }
                            strBuilderRange.append(c);

                            // Need the prev location so when we test the first char after the range
                            // we can get the last one in the range
                            lastLineNo = currLineNo;
                            lastColNo = currColNo;
                        }
                    }

                    // Now advance the counters/trackers for the next char
                    currCharOffset++;

                    if (isFirstChar) {
                        isFirstChar = false;
                    } else if (c == '\n') {
                        currLineNo++;
                        currColNo = 1;

                        if (!continueToLineEnd && currLineNo > 1) {
                            // Multi line data so we want to keep adding chars all the way to the end
                            // of the line so we don't get part lines in the UI.
                            continueToLineEnd = true;
                        }
                    } else {
                        currColNo++;
                    }
                }
            }
        }

        final boolean isTotalPageableItemsExact = currBufferLen == -1;

        currCharOffset = currCharOffset - 1; // undo the last ++ op

        final RowCount<Long> totalCharCount = RowCount.of(
                startCharOffset + strBuilderRange.length(),
                isTotalPageableItemsExact);

        // The range returned may differ to that requested if we have continued to the end of the line
        final DataRange actualDataRange = DataRange.builder()
                .fromCharOffset(startCharOffset)
                .fromLocation(DefaultLocation.of(startLineNo, startColNo))
                .toLocation(DefaultLocation.of(lastLineNo, lastColNo))
                .toCharOffset(currCharOffset)
                .withLength((long) strBuilderRange.length())
                .build();

        // Define the range that we are actually returning, which may be smaller than requested
        final SourceLocation.Builder builder = SourceLocation.builder(sourceLocation.getId())
                .withPartNo(sourceLocation.getPartNo())
                .withChildStreamType(sourceLocation.getOptChildType()
                        .orElse(null))
                .withDataRange(actualDataRange);

        if (segmentNumber != null) {
            builder.withSegmentNumber(segmentNumber);
        }

        final RawResult rawResult = new RawResult(builder.build(), strBuilderRange.toString());
        rawResult.setTotalCharacterCount(totalCharCount);
        return rawResult;
    }

    /**
     * @return True if we are after or on the first char of our range.
     */
    private NonSegmentedIncludeCharPredicate buildInclusiveFromPredicate(final DataRange dataRange) {
        // FROM (inclusive)
        final NonSegmentedIncludeCharPredicate inclusiveFromPredicate;
        if (dataRange == null || !dataRange.hasBoundedStart()) {
            // No start bound
            LOGGER.debug("Unbounded from predicate");
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, charCount) -> true;
        } else if (dataRange.getOptCharOffsetFrom().isPresent()) {
            final long startCharOffset = dataRange.getOptCharOffsetFrom().getAsLong();
            LOGGER.debug("Char offset (inc.) from predicate [{}]", startCharOffset);
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    currCharOffset >= startCharOffset;
        } else if (dataRange.getOptLocationFrom().isPresent()) {
            final int lineNoFrom = dataRange.getOptLocationFrom().get().getLineNo();
            final int colNoFrom = dataRange.getOptLocationFrom().get().getColNo();
            LOGGER.debug("Line/col (inc.) from predicate [{}, {}]", lineNoFrom, colNoFrom);
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    currLineNo > lineNoFrom || (currLineNo == lineNoFrom && currColNo >= colNoFrom);
        } else {
            throw new RuntimeException("No start point specified");
        }
        return inclusiveFromPredicate;
    }

//    private NonSegmentedIncludeCharPredicate buildInclusiveToPredicate(final DataRange dataRange) {
//        // TO (inclusive)
//        final NonSegmentedIncludeCharPredicate inclusiveToPredicate;
//        if (dataRange == null || !dataRange.hasBoundedEnd()) {
//            // No end bound
//            LOGGER.debug("Unbounded to predicate");
//            inclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) -> true;
//        } else if (dataRange.getOptLength().isPresent()) {
//            final long dataLength = dataRange.getOptLength().getAsLong();
//            LOGGER.debug("Length (inc.) to predicate [{}]", dataLength);
//            inclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
//                    charCount <= dataLength;
//        } else if (dataRange.getOptCharOffsetTo().isPresent()) {
//            final long charOffsetTo = dataRange.getOptCharOffsetTo().getAsLong();
//            LOGGER.debug("Char offset (inc.) to predicate [{}]", charOffsetTo);
//            inclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
//                    currCharOffset <= charOffsetTo;
//        } else if (dataRange.getOptLocationTo().isPresent()) {
//            final int lineNoTo = dataRange.getOptLocationTo().get().getLineNo();
//            final int colNoTo = dataRange.getOptLocationTo().get().getColNo();
//            LOGGER.debug("Line/col (inc.) to predicate [{}, {}]", lineNoTo, colNoTo);
//            inclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
//                    currLineNo < lineNoTo || (currLineNo == lineNoTo && currColNo <= colNoTo);
//        } else {
//            throw new RuntimeException("No start point specified");
//        }
//        return inclusiveToPredicate;
//    }

    /**
     * @return True if we have gone past our desired range
     */
    private NonSegmentedIncludeCharPredicate buildExclusiveToPredicate(final DataRange dataRange) {
        // TO (exclusive)

        final NonSegmentedIncludeCharPredicate exclusiveToPredicate;
        if (dataRange == null || !dataRange.hasBoundedEnd()) {
            // No end bound
            LOGGER.debug("Unbounded to predicate");
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    charCount >= MAX_CHARS;
        } else if (dataRange.getOptLength().isPresent()) {
            final long dataLength = dataRange.getOptLength().getAsLong();
            LOGGER.debug("Length (inc.) to predicate [{}]", dataLength);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    charCount > dataLength || charCount >= MAX_CHARS;
        } else if (dataRange.getOptCharOffsetTo().isPresent()) {
            final long charOffsetTo = dataRange.getOptCharOffsetTo().getAsLong();
            LOGGER.debug("Char offset (inc.) to predicate [{}]", charOffsetTo);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    currCharOffset > charOffsetTo || charCount >= MAX_CHARS;
        } else if (dataRange.getOptLocationTo().isPresent()) {
            final int lineNoTo = dataRange.getOptLocationTo().get().getLineNo();
            final int colNoTo = dataRange.getOptLocationTo().get().getColNo();
            LOGGER.debug("Line/col (inc.) to predicate [{}, {}]", lineNoTo, colNoTo);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, charCount) ->
                    currLineNo > lineNoTo
                            || (currLineNo == lineNoTo && currColNo > colNoTo)
                            || charCount >= MAX_CHARS;
        } else {
            throw new RuntimeException("No start point specified");
        }
        return exclusiveToPredicate;
    }

    private String usePipeline(final Source streamSource,
                               final String string,
                               final String feedName,
                               final DocRef pipelineRef,
                               final InputStreamProvider inputStreamProvider) {
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

    private List<String> getAvailableChildStreamTypes(
            final InputStreamProvider inputStreamProvider) throws IOException {

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

    private interface NonSegmentedIncludeCharPredicate {

        boolean test(final long currLineNo,
                     final long currColNo,
                     final long currCharOffset,
                     final long charCount);
    }

    private static class RawResult {
        private final SourceLocation sourceLocation;
        private final String rawData;

//        private OffsetRange<Long> pageItemsRange;
//        private RowCount<Long> totalPageableItems;

        private OffsetRange<Long> itemRange; // part/segment/marker
        private RowCount<Long> totalItemCount; // part/segment/marker
        private RowCount<Long> totalCharacterCount; // Total chars in part/segment

        public RawResult(final SourceLocation sourceLocation,
                         final String rawData) {
            this.sourceLocation = sourceLocation;
            this.rawData = rawData;
        }

        public SourceLocation getSourceLocation() {
            return sourceLocation;
        }

        public String getRawData() {
            return rawData;
        }

//        public OffsetRange<Long> getPageItemsRange() {
//            return pageItemsRange;
//        }

//        public void setPageItemsRange(final OffsetRange<Long> pageItemsRange) {
//            this.pageItemsRange = pageItemsRange;
//        }
//
//        public RowCount<Long> getTotalPageableItems() {
//            return totalPageableItems;
//        }
//
//        public void setTotalPageableItems(final RowCount<Long> totalPageableItems) {
//            this.totalPageableItems = totalPageableItems;
//        }


        public OffsetRange<Long> getItemRange() {
            return itemRange;
        }

        public void setItemRange(final OffsetRange<Long> itemRange) {
            this.itemRange = itemRange;
        }

        public RowCount<Long> getTotalItemCount() {
            return totalItemCount;
        }

        public void setTotalItemCount(final RowCount<Long> totalItemCount) {
            this.totalItemCount = totalItemCount;
        }

        public RowCount<Long> getTotalCharacterCount() {
            return totalCharacterCount;
        }

        public void setTotalCharacterCount(final RowCount<Long> totalCharacterCount) {
            this.totalCharacterCount = totalCharacterCount;
        }
    }

}
