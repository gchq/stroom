
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
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
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
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Location;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;
import stroom.util.string.HexDumpUtil;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.CountingInputStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import javax.validation.constraints.NotNull;
import javax.xml.transform.TransformerException;

public class DataFetcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFetcher.class);

    // TODO @AT Need to implement showing the data has been truncated, either
    //   here by modifying the returned data or by setting some flags in the
    //   result obj to indicate there is more data at the start and/or end
    //   and let the UI do it.
    private static final String TRUNCATED_TEXT = "";
//    private static final String TRUNCATED_TEXT = "...[TRUNCATED IN USER INTERFACE]...";
//    private static final int MAX_LINE_LENGTH = 1000;
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    private static final int STREAM_BUFFER_SIZE = 1024 * 100;

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
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final SourceConfig sourceConfig;
    private final TaskContextFactory taskContextFactory;

    //    private Long index = 0L;
    private Long partCount = 0L;
//    private Long pageOffset = 0L;
//    private Long pageLength = 0L;

    // This is either the number of segments or the number of lines
    private Long pageTotal = 0L;
//    private boolean pageTotalIsExact = false;

    private Long recordIndex;

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
                final SecurityContext securityContext,
                final PipelineScopeRunnable pipelineScopeRunnable,
                final SourceConfig sourceConfig,
                final TaskContextFactory taskContextFactory) {
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
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.sourceConfig = sourceConfig;
        this.taskContextFactory = taskContextFactory;
    }

    public Set<String> getAvailableChildStreamTypes(final long id, final long partNo) {

        return securityContext.useAsReadResult(() -> {
            final Source source = streamStore.openSource(id, true);

            if (source == null) {
                throw new RuntimeException(LogUtil.message("Error opening stream {} - meta not found.", id));
            }

            try {
                final InputStreamProvider inputStreamProvider = source.get(partNo);

                final Set<String> childTypes = inputStreamProvider.getChildTypes();

                return childTypes;

            } catch (IOException e) {
                throw new RuntimeException(LogUtil.message("Error opening stream {}, part {}", id, partNo), e);
            }
        });
    }

    public AbstractFetchDataResult getData(final FetchDataRequest fetchDataRequest) {
        return taskContextFactory.contextResult("Data Fetcher", taskContext -> {
            taskContext.info(() -> "Fetching data for " +
                    fetchDataRequest.getSourceLocation().getMetaId() +
                    ":" +
                    fetchDataRequest.getSourceLocation().getPartIndex() +
                    ":" +
                    fetchDataRequest.getSourceLocation().getRecordIndex());

            LOGGER.debug(() -> LogUtil.message("getData called for {}:{}:{}",
                    fetchDataRequest.getSourceLocation().getMetaId(),
                    fetchDataRequest.getSourceLocation().getPartIndex(),
                    fetchDataRequest.getSourceLocation().getRecordIndex()));

            // Allow users with 'Use' permission to read data, pipelines and XSLT.
            return securityContext.useAsReadResult(() -> {
                Set<String> availableChildStreamTypes = null;
                Meta meta = null;
                String feedName = null;
                String streamTypeName = null;
                final SourceLocation sourceLocation = fetchDataRequest.getSourceLocation();


                // Get the stream source.
                try (final Source source = streamStore.openSource(
                        fetchDataRequest.getSourceLocation().getMetaId(),
                        true)) {

                    meta = source.getMeta();
                    feedName = meta.getFeedName();
                    streamTypeName = meta.getTypeName();

                    if (sourceLocation.getPartIndex() < 0) {
                        // Handle cases during stepping when we have not yet stepped
                        return createEmptyResult(
                                fetchDataRequest,
                                feedName,
                                streamTypeName,
                                sourceLocation,
                                availableChildStreamTypes,
                                null,
                                new Count<>(0L, true));
                    }

                    long partIndex = fetchDataRequest.getSourceLocation().getPartIndex();


                    // Prevent user going past last part
//                    if (partIndex >= partCount) {
//                        partIndex = partCount - 1;
//                    }

                    try (final InputStreamProvider inputStreamProvider = source.get(partIndex)) {
                        // Find out which child stream types are available.
                        availableChildStreamTypes = getAvailableChildStreamTypes(inputStreamProvider);

                        final String requestedChildStreamType = sourceLocation.getOptChildType()
                                .orElse(null);

                        // Establish the number of parts. even though the parts in the child stream should
                        // be the same as the data stream the data stream may be corrupt so this allows
                        // us to view the other child streams
                        // We need to do this after getAvailableChildStreamTypes so if this count fails then we
                        // still know what the other strm types are.
                        partCount = source.count(requestedChildStreamType);

                        if (partIndex >= partCount) {
                            throw new RuntimeException(LogUtil.message(
                                    "Part number requested [{}] is greater than the number of parts [{}]",
                                    partIndex + 1,
                                    partCount));
                        }

                        try (final SegmentInputStream segmentInputStream = inputStreamProvider.get(
                                requestedChildStreamType)) {

                            final long segmentCount = segmentInputStream.count();
                            final boolean isSegmented = segmentCount > 1;

                            // segment no doesn't matter for non-segmented data
                            if (isSegmented && sourceLocation.getRecordIndex() < 0) {
                                // Handle cases during stepping when we have not yet stepped
                                return createEmptyResult(
                                        fetchDataRequest,
                                        feedName,
                                        streamTypeName,
                                        sourceLocation,
                                        availableChildStreamTypes,
                                        null,
                                        new Count<>(segmentCount, true));
                            }

                            // If this is an error stream and the UI is requesting markers then
                            // create a list of markers.
                            if (StreamTypeNames.ERROR.equals(streamTypeName)
                                    && DisplayMode.MARKER.equals(fetchDataRequest.getDisplayMode())) {

                                return createErrorMarkerResult(
                                        feedName,
                                        streamTypeName,
                                        segmentInputStream,
                                        fetchDataRequest.getSourceLocation(),
                                        availableChildStreamTypes,
                                        fetchDataRequest.getExpandedSeverities());
                            } else if (DisplayMode.MARKER.equals(fetchDataRequest.getDisplayMode())) {
                                throw new RuntimeException(LogUtil.message(
                                        "Invalid display mode {} for stream type {}",
                                        fetchDataRequest.getDisplayMode(), streamTypeName));
                            } else {
                                return createDataResult(
                                        feedName,
                                        streamTypeName,
                                        segmentInputStream,
                                        availableChildStreamTypes,
                                        source,
                                        inputStreamProvider,
                                        fetchDataRequest,
                                        taskContext);
                            }
                        }
                    }
                } catch (final IOException | RuntimeException e) {
                    final String message;
                    if (e.getCause() instanceof ClosedByInterruptException) {
                        message = e.getMessage();
                    } else if (meta != null && Status.LOCKED.equals(meta.getStatus())) {
                        message = "You cannot view locked streams.";
                    } else if (meta != null && Status.DELETED.equals(meta.getStatus())) {
                        message = "This data may no longer exist.";
                    } else {
                        message = "Error fetching data: " + e.getMessage();
                    }

                    if (availableChildStreamTypes == null && fetchDataRequest.getSourceLocation() != null) {
                        try (final Source source = streamStore.openSource(
                                fetchDataRequest.getSourceLocation().getMetaId(), true);
                                final InputStreamProvider inputStreamProvider = source.get(
                                        fetchDataRequest.getSourceLocation().getPartIndex())) {
                            // Have a stab at getting the types so we can display all possible tabs
                            // It is possible the partindex is out of range but we will swallow any ex.
                            availableChildStreamTypes = getAvailableChildStreamTypes(inputStreamProvider);
                        } catch (Exception e2) {
                            LOGGER.debug("Error trying to get child stream types", e2);
                        }
                    }

                    LOGGER.debug(message, e);
                    return createEmptyResult(
                            fetchDataRequest,
                            feedName,
                            streamTypeName,
                            sourceLocation,
                            availableChildStreamTypes,
                            Collections.singletonList(message),
                            new Count<>(partCount, true));
                }
            });
        }).get();
    }

    @NotNull
    private FetchDataResult createEmptyResult(final FetchDataRequest fetchDataRequest,
                                              final String feedName,
                                              final String streamTypeName,
                                              final SourceLocation sourceLocation,
                                              final Set<String> childStreamTypes,
                                              final List<String> errors,
                                              final Count<Long> itemCount) {
        final OffsetRange itemRange = new OffsetRange(0L, (long) 1);
        final Count<Long> totalCharCount = Count.zeroLong();

        return new FetchDataResult(
                feedName,
                streamTypeName,
                null,
                sourceLocation,
                itemRange,
                itemCount,
                totalCharCount,
                null,
                childStreamTypes,
                null,
                fetchDataRequest.isShowAsHtml(),
                null,
                fetchDataRequest.getDisplayMode(),
                errors);  // Don't really know segmented state as stream is gone
    }

    private FetchMarkerResult createErrorMarkerResult(final String feedName,
                                                      final String streamTypeName,
                                                      final SegmentInputStream segmentInputStream,
                                                      final SourceLocation sourceLocation,
                                                      final Set<String> availableChildStreamTypes,
                                                      final Severity... expandedSeverities) throws IOException {
        List<Marker> markersList;

        // Get the appropriate encoding for the stream type. No child type as this is error strm
        final String encoding = feedProperties.getEncoding(feedName, streamTypeName, null);

        // Include all segments.
        segmentInputStream.includeAll();

        // Get the data from the stream.
        final Reader reader = new InputStreamReader(segmentInputStream, Charset.forName(encoding));

        // Combine markers into a list.
        markersList = new MarkerListCreator().createFullList(reader, expandedSeverities);

        // Create a list just for the request.
        long pageOffset = sourceLocation.getRecordIndex();
        if (pageOffset >= markersList.size()) {
            pageOffset = markersList.size() - 1;
        }
        final long max = pageOffset + SourceLocation.MAX_ERRORS_PER_PAGE;
        final long totalResults = markersList.size();
        final List<Marker> resultList = new ArrayList<>();

        for (long i = pageOffset; i < max && i < totalResults; i++) {
            resultList.add(markersList.get((int) i));
        }

        final String classification = feedProperties.getDisplayClassification(feedName);
        final OffsetRange itemRange = new OffsetRange(
                sourceLocation.getRecordIndex(),
                (long) resultList.size());
        final Count<Long> totalItemCount = new Count<>(totalResults, true);
        final Count<Long> totalCharCount = new Count<>(0L, true);

        return new FetchMarkerResult(
                feedName,
                streamTypeName,
                classification,
                sourceLocation,
                itemRange,
                totalItemCount,
                totalCharCount,
                availableChildStreamTypes,
                new ArrayList<>(resultList),
                DisplayMode.MARKER,
                null);
    }

    private FetchDataResult createDataResult(final String feedName,
                                             final String streamTypeName,
                                             final SegmentInputStream segmentInputStream,
                                             final Set<String> availableChildStreamTypes,
                                             final Source streamSource,
                                             final InputStreamProvider inputStreamProvider,
                                             final FetchDataRequest fetchDataRequest,
                                             final TaskContext taskContext) throws IOException {
        final SourceLocation sourceLocation = fetchDataRequest.getSourceLocation();
        // Read the input stream into a string.
        // If the input stream has multiple segments then we are going to
        // read it in segment mode.

        final DataType dataType = segmentInputStream.count() > 1
                ? DataType.SEGMENTED
                : DataType.NON_SEGMENTED;

        // Get the appropriate encoding for the stream type.
        final String encoding = feedProperties.getEncoding(
                feedName, streamTypeName, fetchDataRequest.getSourceLocation().getChildType());

        final Charset charset = Charset.forName(encoding);

//        LOGGER.info("Size in bytes: {}, avgBytesPerChar: {}, approxChars: {}",
//                ModelStringUtil.formatIECByteSizeString(segmentInputStream.size()),
//                averageBytesPerChar,
//                segmentInputStream.size() / averageBytesPerChar);

        final RawResult rawResult;

        if (DataType.SEGMENTED.equals(dataType)) {
            rawResult = getSegmentedData(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    fetchDataRequest.getDisplayMode());
        } else {
            // Non-segmented data
            rawResult = getNonSegmentedData(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    fetchDataRequest.getDisplayMode());
        }

        // Useful for testing the displaying of errors in the UI
//        if (!feedName.isBlank()) {
//            throw new RuntimeException("Bad things happened. Lorem ipsum dolor sit amet, consectetur adipiscing " +
//                    "elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim " +
//                    "ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea " +
//                    "commodo consequat.");
//        }

        // We are viewing the data with the BOM removed, so remove the size of the BOM from the
        // total bytes else the progress bar is messed up
        rawResult.setTotalBytes(segmentInputStream.size() - rawResult.byteOrderMarkLength);
        if (rawResult.getTotalCharacterCount() == null) {
            rawResult.setTotalCharacterCount(estimateCharCount(segmentInputStream.size(), charset));
        }

        return buildFetchDataResult(
                feedName,
                streamTypeName,
                availableChildStreamTypes,
                streamSource,
                inputStreamProvider,
                fetchDataRequest,
                dataType,
                rawResult,
                taskContext);
    }

    @NotNull
    private FetchDataResult buildFetchDataResult(final String feedName,
                                                 final String streamTypeName,
                                                 final Set<String> availableChildStreamTypes,
                                                 final Source streamSource,
                                                 final InputStreamProvider inputStreamProvider,
                                                 final FetchDataRequest fetchDataRequest,
                                                 final DataType dataType,
                                                 final RawResult rawResult,
                                                 final TaskContext taskContext) {

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
                        inputStreamProvider,
                        taskContext);

            } catch (final RuntimeException e) {
                output = e.getMessage();
                if (output == null || output.length() == 0) {
                    output = e.toString();
                }
            }

        } else {
            output = rawResult.getRawData();
        }

        // Set the result.
        final String classification = feedProperties.getDisplayClassification(feedName);

        return new FetchDataResult(
                feedName,
                streamTypeName,
                classification,
                rawResult.getSourceLocation(),
                rawResult.getItemRange(),
                rawResult.getTotalItemCount(),
                rawResult.getTotalCharacterCount(),
                rawResult.getTotalBytes(),
                availableChildStreamTypes,
                output,
                fetchDataRequest.isShowAsHtml(),
                dataType,
                rawResult.getDisplayMode(),
                null);
    }

    private Count<Long> estimateCharCount(final long totalBytes, final Charset charset) {
        return Count.approximately((long) Math.floor(
                charset.newDecoder().averageCharsPerByte() * totalBytes));
    }

    private RawResult getSegmentedData(final SourceLocation sourceLocation,
                                       final SegmentInputStream segmentInputStream,
                                       final String encoding,
                                       final DisplayMode displayMode) throws IOException {
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
        recordIndex = sourceLocation.getRecordIndex();
        if (recordIndex >= pageTotal) {
            recordIndex = pageTotal - 1;
        }

        // Include start root element.
        segmentInputStream.include(0);

        // Include the requested segment, add one to allow for start root elm
        segmentInputStream.include(recordIndex + 1);

        // Include end root element.
        segmentInputStream.include(segmentInputStream.count() - 1);

        // Get the data from the stream.
//        return StreamUtil.streamToString(segmentInputStream, Charset.forName(encoding));
        final RawResult rawResult = switch (displayMode) {
            case TEXT -> extractDataRange(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size());
            case HEX -> extractDataRangeAsHex(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size());
            default -> throw new IllegalArgumentException("Unexpected display mode " + displayMode);
        };

        // Override the page items range/total as we are dealing in segments/records
        rawResult.setItemRange(new OffsetRange(recordIndex, 1L));
        rawResult.setTotalItemCount(Count.of(segmentInputStream.count() - 2, true));
        return rawResult;
    }

    private RawResult getNonSegmentedData(final SourceLocation sourceLocation,
                                          final SegmentInputStream segmentInputStream,
                                          final String encoding,
                                          final DisplayMode displayMode) throws IOException {

        final RawResult rawResult = switch (displayMode) {
            case TEXT -> extractDataRange(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size());
            case HEX -> extractDataRangeAsHex(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size());
            default -> throw new IllegalArgumentException("Unexpected display mode " + displayMode);
        };

        // Non-segmented data exists within parts so set the item info
        rawResult.setItemRange(new OffsetRange(sourceLocation.getPartIndex(), 1L));
        rawResult.setTotalItemCount(Count.of(partCount, true));
        return rawResult;
    }

    private RawResult extractDataRangeAsHex(final SourceLocation sourceLocation,
                                            final InputStream inputStream,
                                            final String encoding,
                                            final long streamSizeBytes) throws IOException {

        final CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        // Always use utf8 for the right hand decoded col as we are decoding single bytes at a time.
        // If we used the charset of the feed and the feed is say utf16 (which is all multi-byte) then
        // you will never see anything in the
        final String hexDump = HexDumpUtil.hexDump(
                countingInputStream,
                StandardCharsets.UTF_8,
                sourceConfig.getMaxHexDumpLines());

        final long len = hexDump.length();
        final long bytesRead = countingInputStream.getByteCount();

        final SourceLocation.Builder sourceLocationBuilder = SourceLocation.builder(sourceLocation.getMetaId())
                .withPartIndex(sourceLocation.getPartIndex())
                .withChildStreamType(sourceLocation.getOptChildType()
                        .orElse(null))
                .withHighlight(sourceLocation.getHighlight()) // pass the requested highlight back
                .withDataRange(DataRange.builder()
                        .fromCharOffset(0L)
                        .toCharOffset(len - 1)
                        .toByteOffset(bytesRead - 1)
                        .build());

        final RawResult rawResult = new RawResult(
                sourceLocationBuilder.build(),
                hexDump,
                0,
                DisplayMode.HEX);
        if (bytesRead == streamSizeBytes) {
            rawResult.setTotalCharacterCount(Count.exactly(len));
        } else {
            // Extrapolate the total char count using the chars we have and the proportion
            // of all bytes we consumed to get there
            final long approxCharCount = (streamSizeBytes / bytesRead) * len;
            rawResult.setTotalCharacterCount(Count.approximately(approxCharCount));
        }
        return rawResult;
    }

    private RawResult extractDataRange(final SourceLocation sourceLocation,
                                       final InputStream inputStream,
                                       final String encoding,
                                       final long streamSizeBytes) throws IOException {
        // We could have:
        // One potentially VERY long line, too big to display
        // Lots of small lines
        // Multiple long lines that are too big to display
        StringBuilder strBuilderRange = new StringBuilder();
        final StringBuilder strBuilderLineSoFar = new StringBuilder();

        // Trackers for what we have so far and where we are
        long currByteOffset = 0; // zero based
        int currLineNo = 1; // one based
        int currColNo = 1; // one based
        long currCharOffset = 0; //zero based

        int lastLineNo = -1; // one based
        int lastColNo = -1; // one based
        long startCharOffset = -1;
        int startLineNo = -1;
        int startColNo = -1;
        long charsInRangeCount = 0;

        long startOfCurrLineCharOffset = 0;
        long startByteOffset = -1;

        Optional<DecodedChar> optDecodeChar = Optional.empty();
        Optional<DecodedChar> optLastDecodeChar = Optional.empty();
        boolean isFirstChar = true;
        int extraCharCount = 0;

        boolean foundRange = false;
        boolean isMultiLine = false;
        Count<Long> totalCharCount = Count.of(0L, false);

        // If no range supplied then use a default one
        final DataRange dataRange = sourceLocation.getOptDataRange()
                .orElse(DataRange.fromCharOffset(0, sourceConfig.getMaxCharactersPerFetch()));

        final CharReader charReader = new CharReader(inputStream, false, encoding);

        final NonSegmentedIncludeCharPredicate inclusiveFromPredicate = buildInclusiveFromPredicate(
                dataRange);
        final NonSegmentedIncludeCharPredicate exclusiveToPredicate = buildExclusiveToPredicate(
                dataRange);

        // Ideally we would jump to the requested offset, but if we do, we can't
        // track the line/colcharOffset info for the requested range, i.e.
        // to show the right line numbers in the editor. Thus we need
        // to advance through char by char

        while (true) {
            optLastDecodeChar = optDecodeChar;
            optDecodeChar = charReader.read();
            if (optDecodeChar.isEmpty()) {
                // Reached the end of the stream
                totalCharCount = Count.exactly(currCharOffset + 1); // zero based offset to count
                break;
            }

            final DecodedChar decodedChar = optDecodeChar.get();

            if (inclusiveFromPredicate.test(currLineNo, currColNo, currCharOffset, currByteOffset, charsInRangeCount)) {

                // On or after the first requested char
                foundRange = true;

//                LOGGER.info("{}:{} {}",
//                        currLineNo,
//                        currColNo,
//                        decodedChar.isLineBreak() ? "\\n" : decodedChar.getAsString());

                boolean isCharAfterRequestedRange = exclusiveToPredicate.test(
                        currLineNo, currColNo, currCharOffset, currByteOffset, charsInRangeCount);

                if (isCharAfterRequestedRange) {
                    extraCharCount++;
                }

                // For multi-line data continue past the desired range a bit (up to a limit) to try to complete the line
                // to make it look better in the UI.
                if (isCharAfterRequestedRange
                        && hasReachedLimitOfLineContinuation(decodedChar, currColNo, extraCharCount, isMultiLine)) {
                    // This is the char after our requested range
                    // or requested range continued to the end of the line
                    // or we have blown the max chars limit
                    if (decodedChar.isLineBreak()) {
                        // need to ensure we count the line break in our offset position.
                        currCharOffset += decodedChar.getCharCount();
                    }
                    break;
                } else {
                    // Inside the requested range (or continuing past it a bit to the end of the line)
                    charsInRangeCount += decodedChar.getCharCount();
                    // Record the start position for the requested range
                    if (startCharOffset == -1) {
                        startCharOffset = currCharOffset;
                        startLineNo = currLineNo;
                        startColNo = currColNo;
                        startByteOffset = charReader.getLastByteOffsetRead()
                                .orElseThrow(() -> new RuntimeException("Should have a byte offset at this point"));
                    }
                    strBuilderRange.append(decodedChar.getAsString());

                    // Need the prev location so when we test the first char after the range
                    // we can get the last one in the range
                    lastLineNo = currLineNo;
                    lastColNo = currColNo;
                }
            } else {
                // Hold the chars for the line so far up to the requested range so we can
                // tack it on when we find our range
                strBuilderLineSoFar.append(decodedChar.getAsString());
            }

            // Now advance the counters/trackers for the next char
            currCharOffset += decodedChar.getCharCount();
            currByteOffset += decodedChar.getByteCount();

            if (isFirstChar) {
                isFirstChar = false;
            } else if (decodedChar.isLineBreak()) {
                currLineNo++;
                currColNo = 1;

                if (!isMultiLine && currLineNo > 1) {
                    // Multi line data so we want to keep adding chars all the way to the end
                    // of the line so we don't get part lines in the UI.
                    isMultiLine = true;
                }
                if (!foundRange) {
                    // Not found our requested range yet so reset the current line so far
                    // to the offset of the next char
                    startOfCurrLineCharOffset = currCharOffset;
                    strBuilderLineSoFar.setLength(0);
                }
            } else {
                // This is a debatable one. Is a simple smiley emoji one col or two?
                // Java will say the string has length 2
                currColNo += decodedChar.getCharCount();
            }
        }


        final StringBuilder strBuilderResultRange = new StringBuilder();


//        if (isMultiLine && startLineNo != 1) {
//            // TODO @AT See TODO next to TRUNCATED_TEXT declaration
////            strBuilderResultRange.append(TRUNCATED_TEXT + "\n");
////            startLineNo--;
//        } else if (!isMultiLine && startCharOffset != 0) {
//            strBuilderResultRange.append(TRUNCATED_TEXT);
//        }

        if (isMultiLine && strBuilderRange.length() > 0 && strBuilderRange.charAt(0) != '\n') {
            startCharOffset = startOfCurrLineCharOffset;
            startColNo = 1;
            // Tack on the beginning of the line up to the range
            strBuilderResultRange
                    .append(strBuilderLineSoFar)
                    .append(strBuilderRange);
        } else {
            strBuilderResultRange.append(strBuilderRange);
        }

//        if (isMultiLine && !isTotalPageableItemsExact) {
//            if (currChar == '\n') {
//                strBuilderResultRange.append(TRUNCATED_TEXT);
//            } else {
//                // TODO @AT See TODO next to TRUNCATED_TEXT declaration
////                strBuilderResultRange.append("\n" + TRUNCATED_TEXT);
//            }
//        } else if (!isMultiLine && !isTotalPageableItemsExact) {
//            strBuilderResultRange.append(TRUNCATED_TEXT);
//        }


        if (optDecodeChar.isPresent() && optDecodeChar.filter(DecodedChar::isLineBreak).isPresent()) {
            currCharOffset = currCharOffset - 1; // undo the last ++ op
        }

        // If last char in range was a line break then we need to use currLineNo which includes the new line
        final Location locationTo = optLastDecodeChar.filter(DecodedChar::isLineBreak).isPresent()
                ? DefaultLocation.of(currLineNo, currColNo)
                : DefaultLocation.of(lastLineNo, lastColNo);

        if (!totalCharCount.isExact() && charReader.getLastCharOffsetRead().isPresent()) {
            // Estimate the total char count based on the ratio of chars to bytes seen so far.
            // The estimate will improve as we fetch further into the stream.
            final double avgCharsPerByte = charReader.getLastCharOffsetRead().get()
                    / (double) charReader.getLastByteOffsetRead().get();

            totalCharCount = Count.approximately((long) (avgCharsPerByte * streamSizeBytes));
        }

        // The range returned may differ to that requested if we have continued to the end of the line
        final DataRange actualDataRange;
        final String charData;
        final TextRange highlight;
        // At this point currByteOffset is the offset of the first byte of the char outside our range
        // so subtract one to get the offset of the last byte (may be mult-byte) of the last 'char'
        // in our range
        final long byteOffsetToInc = currByteOffset > 0
                ? currByteOffset - 1
                : currByteOffset;
        if (foundRange) {
            charData = strBuilderResultRange.toString();
            actualDataRange = DataRange.builder()
                    .fromCharOffset(startCharOffset)
                    .fromByteOffset(startByteOffset)
                    .fromLocation(DefaultLocation.of(startLineNo, startColNo))
                    .toLocation(locationTo)
                    .toCharOffset(currCharOffset)
                    .toByteOffset(byteOffsetToInc)
//                    .toByteOffset(charReader.getLastByteOffsetRead()
//                            .orElseThrow())
                    .withLength((long) charData.length())
                    .build();
            highlight = sourceLocation.getHighlight();
        } else if (strBuilderResultRange.length() == 0) {
            actualDataRange = null;
            charData = "## No Data ##";
            highlight = null;
        } else {
            actualDataRange = null;
            charData = "## Error: Requested range not found ##";
            highlight = null;
        }

        // Define the range that we are actually returning, which may be bigger or smaller than requested
        // e.g. if we have continued to the end of the line or we have hit a char limit
        final SourceLocation.Builder builder = SourceLocation.builder(sourceLocation.getMetaId())
                .withPartIndex(sourceLocation.getPartIndex())
                .withChildStreamType(sourceLocation.getOptChildType()
                        .orElse(null))
                .withHighlight(highlight) // pass the requested highlight back
                .withDataRange(actualDataRange);

        if (recordIndex != null) {
            builder.withRecordIndex(recordIndex);
        }

        final SourceLocation resultLocation = builder.build();
        LOGGER.debug(() -> LogUtil.message(
                "resultLocation {}, charData [{}]",
                resultLocation,
                charData.substring(0, Math.min(charData.length(), 100))));

        final int byteOrderMarkLength = charReader.getByteOrderMark()
                .map(ByteOrderMark::length)
                .orElse(0);

        final RawResult rawResult = new RawResult(
                resultLocation,
                charData,
                byteOrderMarkLength,
                DisplayMode.TEXT);
        rawResult.setTotalCharacterCount(totalCharCount);
        return rawResult;
    }

    private boolean hasReachedLimitOfLineContinuation(final DecodedChar decodedChar,
                                                      final int currColNo,
                                                      final int extraCharCount,
                                                      final boolean isMultiLine) {
        // Don't continue to use chars beyond the range if
        //   it is single line data
        //   we have hit our line continuation limit
        //   we are on a line break, i.e our inc. range ended just before or sometime before the end of line
        //   we are on the first char of a line, i.e. our inc. range ended on a line break so this is first char
        //     after that
        return !isMultiLine
                || (extraCharCount > sourceConfig.getMaxCharactersToCompleteLine()
                || decodedChar.isLineBreak()
                || currColNo == 1);
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
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) -> true;
        } else if (dataRange.getOptByteOffsetFrom().isPresent()) {
            final long startByteOffset = dataRange.getOptByteOffsetFrom().get();
            LOGGER.debug("Byte offset (inc.) from predicate [{}]", startByteOffset);
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currByteOffset >= startByteOffset;
        } else if (dataRange.getOptCharOffsetFrom().isPresent()) {
            final long startCharOffset = dataRange.getOptCharOffsetFrom().get();
            LOGGER.debug("Char offset (inc.) from predicate [{}]", startCharOffset);
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currCharOffset >= startCharOffset;
        } else if (dataRange.getOptLocationFrom().isPresent()) {
            final int lineNoFrom = dataRange.getOptLocationFrom().get().getLineNo();
            final int colNoFrom = dataRange.getOptLocationFrom().get().getColNo();
            LOGGER.debug("Line/col (inc.) from predicate [{}, {}]", lineNoFrom, colNoFrom);
            inclusiveFromPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currLineNo > lineNoFrom || (currLineNo == lineNoFrom && currColNo >= colNoFrom);
        } else {
            throw new RuntimeException("No start point specified");
        }
        return inclusiveFromPredicate;
    }

    /**
     * @return True if we have gone past our desired range
     */
    private NonSegmentedIncludeCharPredicate buildExclusiveToPredicate(final DataRange dataRange) {
        // TO (exclusive)

        long maxChars = sourceConfig.getMaxCharactersPerFetch();

        final NonSegmentedIncludeCharPredicate exclusiveToPredicate;
        if (dataRange == null || !dataRange.hasBoundedEnd()) {
            // No end bound
            LOGGER.debug("Unbounded to predicate");
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    charCount >= maxChars;
        } else if (dataRange.getOptLength().isPresent()) {
            final long dataLength = dataRange.getOptLength().get();
            LOGGER.debug("Length (inc.) to predicate [{}]", dataLength);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    charCount > dataLength || charCount >= maxChars;
        } else if (dataRange.getOptByteOffsetTo().isPresent()) {
            final long byteOffsetTo = dataRange.getOptByteOffsetTo().get();
            LOGGER.debug("Byte offset (inc.) to predicate [{}]", byteOffsetTo);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currByteOffset > byteOffsetTo || charCount >= maxChars;
        } else if (dataRange.getOptCharOffsetTo().isPresent()) {
            final long charOffsetTo = dataRange.getOptCharOffsetTo().get();
            LOGGER.debug("Char offset (inc.) to predicate [{}]", charOffsetTo);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currCharOffset > charOffsetTo || charCount >= maxChars;
        } else if (dataRange.getOptLocationTo().isPresent()) {
            final int lineNoTo = dataRange.getOptLocationTo().get().getLineNo();
            final int colNoTo = dataRange.getOptLocationTo().get().getColNo();
            LOGGER.debug("Line/col (inc.) to predicate [{}, {}]", lineNoTo, colNoTo);
            exclusiveToPredicate = (currLineNo, currColNo, currCharOffset, currByteOffset, charCount) ->
                    currLineNo > lineNoTo
                            || (currLineNo == lineNoTo && currColNo > colNoTo)
                            || charCount >= maxChars;
        } else {
            throw new RuntimeException("No start point specified");
        }
        return exclusiveToPredicate;
    }

    private String usePipeline(final Source streamSource,
                               final String string,
                               final String feedName,
                               final DocRef pipelineRef,
                               final InputStreamProvider inputStreamProvider,
                               final TaskContext taskContext) {
        return pipelineScopeRunnable.scopeResult(() -> {
            try {

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
                final Pipeline pipeline = pipelineFactory.create(pipelineData, taskContext);

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

                final String data = baos.toString(StreamUtil.DEFAULT_CHARSET_NAME);

                if (!errorReceiver.isAllOk()) {
                    throw new TransformerException(errorReceiver.toString());
                }

                return data;
            } catch (final IOException | TransformerException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private Set<String> getAvailableChildStreamTypes(
            final InputStreamProvider inputStreamProvider) throws IOException {

        return inputStreamProvider.getChildTypes();
    }

    private interface NonSegmentedIncludeCharPredicate {

        boolean test(final long currLineNo,
                     final long currColNo,
                     final long currCharOffset,
                     final long currByteOffset,
                     final long charCount);
    }

    private static class RawResult {

        private final SourceLocation sourceLocation;
        private final String rawData;
        private final int byteOrderMarkLength;
        private final DisplayMode displayMode;

        private OffsetRange itemRange; // part/segment/marker
        private Count<Long> totalItemCount; // part/segment/marker
        private Count<Long> totalCharacterCount; // Total chars in part/segment
        private long totalBytes;

        public RawResult(final SourceLocation sourceLocation,
                         final String rawData,
                         final int byteOrderMarkLength,
                         final DisplayMode displayMode) {
            this.sourceLocation = sourceLocation;
            this.rawData = rawData;
            this.byteOrderMarkLength = byteOrderMarkLength;
            this.displayMode = displayMode;
        }

        public SourceLocation getSourceLocation() {
            return sourceLocation;
        }

        public String getRawData() {
            return rawData;
        }

        /**
         * @return Length in bytes of the byte order mark or 0 if there isn't one
         */
        public int getByteOrderMarkLength() {
            return byteOrderMarkLength;
        }

        public OffsetRange getItemRange() {
            return itemRange;
        }

        public void setItemRange(final OffsetRange itemRange) {
            this.itemRange = itemRange;
        }

        public Count<Long> getTotalItemCount() {
            return totalItemCount;
        }

        public void setTotalItemCount(final Count<Long> totalItemCount) {
            this.totalItemCount = totalItemCount;
        }

        public Count<Long> getTotalCharacterCount() {
            return totalCharacterCount;
        }

        public void setTotalCharacterCount(final Count<Long> totalCharacterCount) {
            this.totalCharacterCount = totalCharacterCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public void setTotalBytes(final long totalBytes) {
            this.totalBytes = totalBytes;
        }

        public DisplayMode getDisplayMode() {
            return displayMode;
        }
    }

}
