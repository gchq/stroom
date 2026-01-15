
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
import stroom.pipeline.state.CurrentUserHolder;
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
import stroom.util.shared.NullSafe;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;
import stroom.util.shared.string.HexDump;
import stroom.util.shared.string.HexDumpLine;
import stroom.util.string.HexDumpUtil;

import jakarta.inject.Provider;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.ByteOrderMark;

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
import java.util.stream.Collectors;
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
    private static final DisplayMode DEFAULT_DISPLAY_MODE = DisplayMode.TEXT;
    // Always use ascii for the right hand decoded col as we are decoding single bytes at a time.
    // If we used the charset of the feed and the feed is say utf16 (which is all multi-byte) then
    // you will never see anything in the
    private static final Charset HEX_DUMP_CHARSET = StandardCharsets.US_ASCII;

    private final Long partsToReturn = 1L;
    private final Long segmentsToReturn = 1L;

    private final Store streamStore;
    private final FeedProperties feedProperties;
    private final Provider<FeedHolder> feedHolderProvider;
    private final Provider<MetaDataHolder> metaDataHolderProvider;
    private final Provider<PipelineHolder> pipelineHolderProvider;
    private final Provider<MetaHolder> metaHolderProvider;
    private final Provider<CurrentUserHolder> currentUserHolderProvider;
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
                final Provider<CurrentUserHolder> currentUserHolderProvider,
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
        this.currentUserHolderProvider = currentUserHolderProvider;
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
            try (final Source source = streamStore.openSource(id, true)) {
                if (source == null) {
                    throw new RuntimeException(LogUtil.message("Error opening stream {} - meta not found.", id));
                }

                try (final InputStreamProvider inputStreamProvider = source.get(partNo)) {
                    return inputStreamProvider.getChildTypes();
                }
            } catch (final IOException e) {
                throw new RuntimeException(LogUtil.message("Error opening stream {}, part {}", id, partNo), e);
            }
        });
    }

    public AbstractFetchDataResult getData(final FetchDataRequest fetchDataRequest) {
        return taskContextFactory.contextResult("Data Fetcher", taskContext -> {
            taskContext.info(() -> "Fetching data for stream_id=" +
                                   fetchDataRequest.getSourceLocation().getMetaId() +
                                   ", part=" +
                                   fetchDataRequest.getSourceLocation().getPartIndex() +
                                   ", record=" +
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

                    final long partIndex = fetchDataRequest.getSourceLocation().getPartIndex();


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
                            final AbstractFetchDataResult result;
                            if (StreamTypeNames.ERROR.equals(streamTypeName)
                                && (fetchDataRequest.getDisplayMode() == null
                                    || DisplayMode.MARKER.equals(fetchDataRequest.getDisplayMode()))) {

                                result = createErrorMarkerResult(
                                        feedName,
                                        streamTypeName,
                                        segmentInputStream,
                                        fetchDataRequest.getSourceLocation(),
                                        availableChildStreamTypes,
                                        fetchDataRequest.getExpandedSeverities());
                            } else if (DisplayMode.MARKER.equals(fetchDataRequest.getDisplayMode())) {
                                final String msg = LogUtil.message(
                                        "Invalid display mode {} for stream type {}",
                                        fetchDataRequest.getDisplayMode(), streamTypeName);
                                LOGGER.error(msg);
                                throw new RuntimeException(msg);
                            } else {
                                result = createDataResult(
                                        feedName,
                                        streamTypeName,
                                        segmentInputStream,
                                        availableChildStreamTypes,
                                        source,
                                        inputStreamProvider,
                                        fetchDataRequest,
                                        taskContext);
                            }
                            return result;
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
                        } catch (final Exception e2) {
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
                getDisplayModeOrDefault(fetchDataRequest),
                errors);  // Don't really know segmented state as stream is gone
    }

    private static DisplayMode getDisplayModeOrDefault(final FetchDataRequest fetchDataRequest) {
        return NullSafe.getOrElse(fetchDataRequest, FetchDataRequest::getDisplayMode, DEFAULT_DISPLAY_MODE);
    }

    private FetchMarkerResult createErrorMarkerResult(final String feedName,
                                                      final String streamTypeName,
                                                      final SegmentInputStream segmentInputStream,
                                                      final SourceLocation sourceLocation,
                                                      final Set<String> availableChildStreamTypes,
                                                      final Severity... expandedSeverities) throws IOException {
        final List<Marker> markersList;

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
        final DisplayMode displayMode = getDisplayModeOrDefault(fetchDataRequest);

        final boolean hasPipeline = fetchDataRequest.getPipeline() != null;
        final SourceLocation sourceLocation;
        if (fetchDataRequest.getSourceLocation().getOptDataRange().isPresent()) {
            sourceLocation = fetchDataRequest.getSourceLocation();
        } else {
            // If no range supplied then use a default one.
            // If there is a pipeline then we need to get all the data then limit the output of that.
            final DataRange newDataRange = hasPipeline
                    ? DataRange.fromCharOffset(0)
                    : DataRange.fromCharOffset(0, sourceConfig.getMaxCharactersPerFetch());
            sourceLocation = fetchDataRequest.getSourceLocation().copy()
                    .withDataRange(newDataRange)
                    .build();
        }

        if (DataType.SEGMENTED.equals(dataType)) {
            rawResult = getSegmentedData(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    displayMode,
                    !hasPipeline);
        } else {
            // Non-segmented data
            rawResult = getNonSegmentedData(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    displayMode,
                    !hasPipeline);
        }

        // Useful for testing the displaying of errors in the UI
//        if (!feedName.isBlank()) {
//            throw new RuntimeException("Bad things happened. Lorem ipsum dolor sit amet, consectetur adipiscing " +
//                    "elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim " +
//                    "ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea " +
//                    "commodo consequat.");
//        }

        // BOM not removed in hex mode (so we can see it)
        if (!DisplayMode.HEX.equals(displayMode)) {
            // We are viewing the data with the BOM removed, so remove the size of the BOM from the
            // total bytes else the progress bar is messed up
            rawResult.setTotalBytes(segmentInputStream.size() - rawResult.byteOrderMarkLength);
        }
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
                                       final DisplayMode displayMode,
                                       final boolean limitChars) throws IOException {
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
                    segmentInputStream.size(),
                    limitChars);
            case HEX -> extractDataRangeAsHex(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size(),
                    DataType.SEGMENTED);
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
                                          final DisplayMode displayMode,
                                          final boolean limitChars) throws IOException {

        final RawResult rawResult = switch (displayMode) {
            case TEXT -> extractDataRange(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size(),
                    limitChars);
            case HEX -> extractDataRangeAsHex(
                    sourceLocation,
                    segmentInputStream,
                    encoding,
                    segmentInputStream.size(),
                    DataType.NON_SEGMENTED);
            default -> throw new IllegalArgumentException("Unexpected display mode " + displayMode);
        };

        // Non-segmented data exists within parts so set the item info
        rawResult.setItemRange(new OffsetRange(sourceLocation.getPartIndex(), 1L));
        rawResult.setTotalItemCount(Count.of(partCount, true));
        return rawResult;
    }

    private HexDump getHexDump(final InputStream inputStream,
                               final DataRange dataRange,
                               final String encoding,
                               final long streamSizeBytes) throws IOException {
        final HexDump hexDump;
        if (dataRange.hasBoundedStart()) {
            // The byte offset is our preferred method of setting the range
            // as it will tally exactly with the TEXT view.
            if (dataRange.getOptByteOffsetFrom().isPresent()) {
                hexDump = HexDumpUtil.hexDump(
                        inputStream,
                        HEX_DUMP_CHARSET,
                        dataRange.getByteOffsetFrom(),
                        sourceConfig.getMaxHexDumpLines());
            } else if (dataRange.getOptCharOffsetFrom().isPresent()) {
                // This is a bit of an approximate location as we don't know how the chars map to the bytes.
                final Charset charset = Charset.forName(encoding);
                final Long estimatedCharCount = estimateCharCount(streamSizeBytes, charset).getCount();
                final double relativeStartPos = dataRange.getCharOffsetFrom() / ((double) estimatedCharCount);
                final long estimatedStartByteOffset = (long) (relativeStartPos * streamSizeBytes);

                LOGGER.debug(() -> LogUtil.message(
                        "streamSizeBytes: {}, charOffsetFrom: {}, " +
                        "estimatedCharCount: {}, relativeStartPos: {}, estimatedStartByteOffset: {}",
                        streamSizeBytes,
                        dataRange.getCharOffsetFrom(),
                        estimatedCharCount,
                        relativeStartPos,
                        estimatedStartByteOffset)
                );

                hexDump = HexDumpUtil.hexDump(
                        inputStream,
                        HEX_DUMP_CHARSET,
                        estimatedStartByteOffset,
                        sourceConfig.getMaxHexDumpLines());
            } else if (dataRange.getOptLocationFrom().isPresent()) {
                LOGGER.warn("Setting hex dump location by line no is not yet supported");
                hexDump = HexDumpUtil.hexDump(
                        inputStream,
                        HEX_DUMP_CHARSET,
                        sourceConfig.getMaxHexDumpLines());
            } else {
                hexDump = HexDumpUtil.hexDump(
                        inputStream,
                        HEX_DUMP_CHARSET,
                        sourceConfig.getMaxHexDumpLines());
            }
        } else {
            hexDump = HexDumpUtil.hexDump(
                    inputStream,
                    HEX_DUMP_CHARSET,
                    sourceConfig.getMaxHexDumpLines());
        }
        return hexDump;
    }

    private List<DataRange> calculateHexDumpHighlights(final DataRange requestedHighlight) {
        if (requestedHighlight == null) {
            return null;
        } else {
            if (requestedHighlight.getOptByteOffsetFrom().isPresent()
                && requestedHighlight.getOptByteOffsetTo().isPresent()) {

                return HexDumpUtil.calculateHighlights(
                                requestedHighlight.getOptByteOffsetFrom().get(),
                                requestedHighlight.getOptByteOffsetTo().get())
                        .stream()
                        .map(textRange -> DataRange.builder()
                                .fromLocation(textRange.getFrom())
                                .toLocation(textRange.getTo())
                                .build())
                        .collect(Collectors.toList());
            } else {
                // Don't have a byte range to work with so can't do anything
                return List.of(requestedHighlight);
            }
        }
    }

    private RawResult extractDataRangeAsHex(final SourceLocation sourceLocation,
                                            final InputStream inputStream,
                                            final String encoding,
                                            final long streamSizeBytes,
                                            final DataType dataType) throws IOException {

        final DataRange dataRange = sourceLocation.getOptDataRange()
                .orElseGet(() -> DataRange.fromCharOffset(0));

        final HexDump hexDump = getHexDump(inputStream, dataRange, encoding, streamSizeBytes);

        if (!hexDump.isEmpty()) {
            // Dump is not empty at this point so Optional::get is ok
            final HexDumpLine firstLine = hexDump.getFirstLine().get();
            final HexDumpLine lastLine = hexDump.getLastLine().get();
            // This is the char offset of the hex dump not the actual data
            // so calculate it from the number of hex dump chars on each line.
            final long fromCharOffset = Math.max(
                    0,
                    ((long) (firstLine.getLineNo() - 1) * HexDump.MAX_CHARS_PER_DUMP_LINE)); // -1 for count => offset
            final long toCharOffset = fromCharOffset + hexDump.getDumpCharCount() - 1; //-1 for count => offset

            final DataRange requestedHighlight = sourceLocation.getHighlights() != null
                                                 && !sourceLocation.getHighlights().isEmpty()
                    ? sourceLocation.getHighlights().get(0)
                    : null;
            final List<DataRange> actualHighlights = calculateHexDumpHighlights(requestedHighlight);

            final SourceLocation.Builder sourceLocationBuilder = SourceLocation.builder(sourceLocation.getMetaId())
                    .withPartIndex(sourceLocation.getPartIndex())
                    .withChildStreamType(sourceLocation.getOptChildType()
                            .orElse(null))
                    .withHighlight(actualHighlights) // pass the modified highlight back
                    .withDataRange(DataRange.builder()
                            .fromCharOffset(fromCharOffset)
                            .toCharOffset(toCharOffset)
                            .fromByteOffset(hexDump.getByteOffsetRange().getFrom())
                            .toByteOffset(hexDump.getByteOffsetRange().getTo() - 1) // ex to inc
                            .fromLocation(DefaultLocation.of(firstLine.getLineNo(), 1))
                            .toLocation(DefaultLocation.of(lastLine.getLineNo(), lastLine.getDumpLineCharCount()))
                            .withLength(hexDump.getDumpCharCount())
                            .build());

            final RawResult rawResult = new RawResult(
                    sourceLocationBuilder.build(),
                    hexDump.getHexDumpAsStr(),
                    0,
                    DisplayMode.HEX);

            rawResult.setTotalCharacterCount(Count.exactly(hexDump.getDumpCharCount()));
            final long totalBytes = DataType.SEGMENTED.equals(dataType)
                    ? hexDump.getDumpByteCount()
                    : streamSizeBytes;
            rawResult.setTotalBytes(totalBytes);
            return rawResult;
        } else {
            return new RawResult(
                    null,
                    "## No Data ##",
                    0,
                    DisplayMode.HEX);
        }
    }


    private RawResult extractDataRange(final SourceLocation sourceLocation,
                                       final InputStream inputStream,
                                       final String encoding,
                                       final long streamSizeBytes,
                                       final boolean limitChars) throws IOException {
        // We could have:
        // One potentially VERY long line, too big to display
        // Lots of small lines
        // Multiple long lines that are too big to display
        final StringBuilder strBuilderRange = new StringBuilder();
        final CharReader charReader = new CharReader(inputStream, false, encoding);
        final DataRange dataRange = sourceLocation.getDataRange();

        final NonSegmentedIncludeCharPredicate inclusiveFromPredicate = buildInclusiveFromPredicate(
                dataRange, true);
        final NonSegmentedIncludeCharPredicate exclusiveToPredicate = buildExclusiveToPredicate(
                dataRange, limitChars);

        // Currently, only support a single highlight for text data
        final DataRange highlight = sourceLocation.getFirstHighlight();
        // A previous call may have already decorated the highlight byte offsets.
        final boolean isHighlightDecorationRequired = highlight != null
                                                      && highlight.getOptByteOffsetFrom().isEmpty()
                                                      && highlight.getOptByteOffsetTo().isEmpty();

        final NonSegmentedIncludeCharPredicate highlightInclusiveFromPredicate = buildInclusiveFromPredicate(
                highlight, false);
        final NonSegmentedIncludeCharPredicate highlightExclusiveToPredicate = buildExclusiveToPredicate(
                highlight, false);

        // Ideally we would jump to the requested offset, but if we do, we can't
        // track the line/colcharOffset info for the requested range, i.e.
        // to show the right line numbers in the editor. Thus we need
        // to advance through char by char

        final ExtractionTracker tracker = new ExtractionTracker(charReader);
        while (true) {
            // Read the next char if there is one
            if (!tracker.readChar()) {
                break;
            }
            final DecodedChar decodedChar = tracker.optDecodeChar.get();

            if (isHighlightDecorationRequired) {
                // As we read the data mark the start/end of the highlight so we can establish the highlight
                // in terms of byte offsets rather than line/col, which allows us to highlight hex dumps.
                if (tracker.highlightStartByteOffsetInc > -1 || highlightInclusiveFromPredicate.test(tracker)) {
                    // Inside the highlighted section
                    if (tracker.highlightStartByteOffsetInc == -1) {
                        tracker.markHighlightStart();
                    }
                    if (tracker.highlightEndByteOffsetExc == -1 && highlightExclusiveToPredicate.test(tracker)) {
                        // Just past the end of highlight section
                        tracker.markHighlightEnd();
                    }
                }
            }

            if (inclusiveFromPredicate.test(tracker)) {

                // On or after the first requested char
                tracker.foundRange = true;

//                LOGGER.info("{}:{} {}",
//                        currLineNo,
//                        currColNo,
//                        decodedChar.isLineBreak() ? "\\n" : decodedChar.getAsString());

                final boolean isCharAfterRequestedRange = exclusiveToPredicate.test(tracker);

                if (isCharAfterRequestedRange) {
                    tracker.extraCharCount++;
                }

                // For multi-line data continue past the desired range a bit (up to a limit) to try
                // to complete the line to make it look better in the UI.
                if (isCharAfterRequestedRange
                    && tracker.hasReachedLimitOfLineContinuation(sourceConfig)) {
                    // This is the char after our requested range
                    // or requested range continued to the end of the line
                    // or we have blown the max chars limit
                    if (decodedChar.isLineBreak()) {
                        // need to ensure we count the line break in our offset position.
                        tracker.currCharOffset += decodedChar.getCharCount();
                    }
                    break;
                } else {
                    // Inside the requested range (or continuing past it a bit to the end of the line)
                    tracker.markInsideRange();
                    strBuilderRange.append(decodedChar.getAsString());
                }
            } else {
                // Hold the chars for the line so far up to the requested range so we can
                // tack it on when we find our range
                tracker.appendCharToCurrentLine();
            }

            tracker.prepareForNextRead();
        }

        final StringBuilder strBuilderResultRange = new StringBuilder();

        if (tracker.isMultiLine && strBuilderRange.length() > 0 && strBuilderRange.charAt(0) != '\n') {
            tracker.startCharOffset = tracker.startOfCurrLineCharOffset;
            tracker.startColNo = 1;
            // Tack on the beginning of the line, up to the range
            tracker.addCurrentLine(strBuilderResultRange);
            strBuilderResultRange.append(strBuilderRange);
        } else {
            strBuilderResultRange.append(strBuilderRange);
        }

        final Location locationTo = tracker.getLocationTo();

        if (!tracker.totalCharCount.isExact() && charReader.getLastCharOffsetRead().isPresent()) {
            // Estimate the total char count based on the ratio of chars to bytes seen so far.
            // The estimate will improve as we fetch further into the stream.
            final double avgCharsPerByte = charReader.getLastCharOffsetRead().get()
                                           / (double) charReader.getLastByteOffsetRead().get();

            tracker.totalCharCount = Count.approximately((long) (avgCharsPerByte * streamSizeBytes));
        }

        // The range returned may differ to that requested if we have continued to the end of the line
        final DataRange actualDataRange;
        final String charData;
        DataRange actualHighlight;
        // At this point currByteOffset is the offset of the first byte of the char outside our range
        // so subtract one to get the offset of the last byte (may be mult-byte) of the last 'char'
        // in our range
        final long byteOffsetToInc = tracker.currByteOffset > 0
                ? tracker.currByteOffset - 1
                : tracker.currByteOffset;
        if (tracker.foundRange) {
            charData = strBuilderResultRange.toString();
            actualDataRange = DataRange.builder()
                    .fromCharOffset(tracker.startCharOffset)
                    .fromByteOffset(tracker.startByteOffset)
                    .fromLocation(DefaultLocation.of(tracker.startLineNo, tracker.startColNo))
                    .toLocation(locationTo)
                    .toCharOffset(tracker.currCharOffset)
                    .toByteOffset(byteOffsetToInc)
//                    .toByteOffset(charReader.getLastByteOffsetRead()
//                            .orElseThrow())
                    .withLength((long) charData.length())
                    .build();
            actualHighlight = highlight;
        } else if (strBuilderResultRange.length() == 0) {
            actualDataRange = null;
            charData = "## No Data ##";
            actualHighlight = null;
        } else {
            actualDataRange = null;
            charData = "## Error: Requested range not found ##";
            actualHighlight = null;
        }

        // Decorate the input highlight with byte offsets in case future calls are in hex mode
        // which can only work in byte terms.
        actualHighlight = isHighlightDecorationRequired && tracker.foundHighlight
                ? NullSafe.get(highlight, highlight2 ->
                highlight2.copy()
                        .fromByteOffset(tracker.highlightStartByteOffsetInc)
                        .toByteOffset(tracker.highlightEndByteOffsetExc - 1)
                        .build())
                : highlight;

        LOGGER.debug("highlight: {}", actualHighlight);

        // Define the range that we are actually returning, which may be bigger or smaller than requested
        // e.g. if we have continued to the end of the line or we have hit a char limit
        final SourceLocation.Builder builder = SourceLocation.builder(sourceLocation.getMetaId())
                .withPartIndex(sourceLocation.getPartIndex())
                .withChildStreamType(sourceLocation.getOptChildType()
                        .orElse(null))
                .withHighlight(actualHighlight) // pass the requested highlight back
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
        rawResult.setTotalCharacterCount(tracker.totalCharCount);
        return rawResult;
    }

    /**
     * @return True if we are after or on the first char of our range.
     */
    private NonSegmentedIncludeCharPredicate buildInclusiveFromPredicate(final DataRange dataRange,
                                                                         final boolean nullRangeResult) {
        // FROM (inclusive)
        final NonSegmentedIncludeCharPredicate inclusiveFromPredicate;
        if (dataRange == null) {
            LOGGER.debug("null dataRange, predicate returns {}", nullRangeResult);
            inclusiveFromPredicate = tracker -> nullRangeResult;
        } else if (!dataRange.hasBoundedStart()) {
            // No start bound
            LOGGER.debug("Unbounded from predicate");
            inclusiveFromPredicate = tracker -> true;
        } else if (dataRange.getOptByteOffsetFrom().isPresent()) {
            final long startByteOffset = dataRange.getOptByteOffsetFrom().get();
            LOGGER.debug("Byte offset (inc.) from predicate [{}]", startByteOffset);
            inclusiveFromPredicate = tracker ->
                    tracker.currByteOffset >= startByteOffset;
        } else if (dataRange.getOptCharOffsetFrom().isPresent()) {
            final long startCharOffset = dataRange.getOptCharOffsetFrom().get();
            LOGGER.debug("Char offset (inc.) from predicate [{}]", startCharOffset);
            inclusiveFromPredicate = tracker ->
                    tracker.currCharOffset >= startCharOffset;
        } else if (dataRange.getOptLocationFrom().isPresent()) {
            final int lineNoFrom = dataRange.getOptLocationFrom().get().getLineNo();
            final int colNoFrom = dataRange.getOptLocationFrom().get().getColNo();
            LOGGER.debug("Line/col (inc.) from predicate [{}, {}]", lineNoFrom, colNoFrom);
            inclusiveFromPredicate = tracker ->
                    tracker.currLineNo > lineNoFrom
                    || (tracker.currLineNo == lineNoFrom && tracker.currColNo >= colNoFrom);
        } else {
            throw new RuntimeException("No start point specified");
        }
        return inclusiveFromPredicate;
    }

    /**
     * @return True if we have gone past our desired range
     */
    private NonSegmentedIncludeCharPredicate buildExclusiveToPredicate(final DataRange dataRange,
                                                                       final boolean limitChars) {
        // TO (exclusive)

        final long maxChars = limitChars
                ? sourceConfig.getMaxCharactersPerFetch()
                : Long.MAX_VALUE;

        final NonSegmentedIncludeCharPredicate exclusiveToPredicate;
        if (dataRange == null || !dataRange.hasBoundedEnd()) {
            // No end bound
            LOGGER.debug("Unbounded to predicate");
            exclusiveToPredicate = tracker ->
                    tracker.charsInRangeCount >= maxChars;
        } else if (dataRange.getOptLength().isPresent()) {
            final long dataLength = dataRange.getOptLength().get();
            LOGGER.debug("Length (inc.) to predicate [{}]", dataLength);
            exclusiveToPredicate = tracker ->
                    tracker.charsInRangeCount > dataLength || tracker.charsInRangeCount >= maxChars;
        } else if (dataRange.getOptByteOffsetTo().isPresent()) {
            final long byteOffsetTo = dataRange.getOptByteOffsetTo().get();
            LOGGER.debug("Byte offset (inc.) to predicate [{}]", byteOffsetTo);
            exclusiveToPredicate = tracker ->
                    tracker.currByteOffset > byteOffsetTo || tracker.charsInRangeCount >= maxChars;
        } else if (dataRange.getOptCharOffsetTo().isPresent()) {
            final long charOffsetTo = dataRange.getOptCharOffsetTo().get();
            LOGGER.debug("Char offset (inc.) to predicate [{}]", charOffsetTo);
            exclusiveToPredicate = tracker ->
                    tracker.currCharOffset > charOffsetTo || tracker.charsInRangeCount >= maxChars;
        } else if (dataRange.getOptLocationTo().isPresent()) {
            final int lineNoTo = dataRange.getOptLocationTo().get().getLineNo();
            final int colNoTo = dataRange.getOptLocationTo().get().getColNo();
            LOGGER.debug("Line/col (inc.) to predicate [{}, {}]", lineNoTo, colNoTo);
            exclusiveToPredicate = tracker ->
                    tracker.currLineNo > lineNoTo
                    || (tracker.currLineNo == lineNoTo && tracker.currColNo > colNoTo)
                    || tracker.charsInRangeCount >= maxChars;
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
                currentUserHolderProvider.get()
                        .setCurrentUser(securityContext.getUserIdentity());

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
                    throw ProcessException.create("Pipeline has no writer");
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
                    processException = ProcessException.create(e.getMessage());
                    throw processException;
                } finally {
                    try {
                        pipeline.process(inputStream, StreamUtil.DEFAULT_CHARSET_NAME);
                    } catch (final RuntimeException e) {
                        if (processException != null) {
                            processException.addSuppressed(e);
                        } else {
                            processException = ProcessException.create(e.getMessage());
                            throw processException;
                        }
                    } finally {
                        try {
                            pipeline.endProcessing();
                        } catch (final RuntimeException e) {
                            if (processException != null) {
                                processException.addSuppressed(e);
                            } else {
                                processException = ProcessException.create(e.getMessage());
                                throw processException;
                            }
                        } finally {
                            bos.flush();
                            bos.close();
                        }
                    }
                }

                String data = baos.toString(StreamUtil.DEFAULT_CHARSET_NAME);
                // Now limit the output of the pipeline
                if (data.length() > sourceConfig.getMaxCharactersPerFetch()) {
                    data = data.substring(0, (int) sourceConfig.getMaxCharactersPerFetch());
                }

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


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private interface NonSegmentedIncludeCharPredicate {

        boolean test(final ExtractionTracker tracker);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * Holds state for the extracting all/some data from a non-segmented inputStream
     */
    private static class ExtractionTracker {

        final CharReader charReader;
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
        boolean foundHighlight = false;
        boolean isMultiLine = false;
        Count<Long> totalCharCount = Count.of(0L, false);

        long highlightStartByteOffsetInc = -1;
        long highlightEndByteOffsetExc = -1;

        final StringBuilder strBuilderLineSoFar = new StringBuilder();

        public ExtractionTracker(final CharReader charReader) {
            this.charReader = charReader;
        }

        /**
         * @return True if a decoded char was read.
         */
        private boolean readChar() throws IOException {
            optLastDecodeChar = optDecodeChar;
            optDecodeChar = charReader.read();
            if (optDecodeChar.isEmpty()) {
                // Reached the end of the stream
                totalCharCount = Count.exactly(currCharOffset + 1); // zero based offset to count
                return false;
            } else {
                return true;
            }
        }

        private void markHighlightStart() {
            highlightStartByteOffsetInc = currByteOffset;
            foundHighlight = true;
        }

        private void markHighlightEnd() {
            highlightEndByteOffsetExc = currByteOffset;
        }

        private void appendCharToCurrentLine() {
            optDecodeChar.ifPresent(decodedChar ->
                    strBuilderLineSoFar.append(decodedChar.getAsString()));
        }

        private void clearCurrentLine() {
            strBuilderLineSoFar.setLength(0);
        }

        private void addCurrentLine(final StringBuilder stringBuilder) {
            stringBuilder.append(strBuilderLineSoFar);
        }

        private boolean isCurrCharALineBreak() {
            return optDecodeChar.filter(DecodedChar::isLineBreak).isPresent();
        }

        private Location getLocationTo() {
            if (isCurrCharALineBreak()) {
                currCharOffset = currCharOffset - 1; // undo the last ++ op
            }

            final boolean lastCharIsLineBreak = optLastDecodeChar.filter(DecodedChar::isLineBreak).isPresent();

            // If last char in range was a line break then we need to use currLineNo which includes the new line
            return lastCharIsLineBreak
                    ? DefaultLocation.of(currLineNo, currColNo)
                    : DefaultLocation.of(lastLineNo, lastColNo);
        }

        private boolean hasReachedLimitOfLineContinuation(final SourceConfig sourceConfig) {
            // Don't continue to use chars beyond the range if
            //   it is single line data
            //   we have hit our line continuation limit
            //   we are on a line break, i.e our inc. range ended just before or sometime before the end of line
            //   we are on the first char of a line, i.e. our inc. range ended on a line break so this is first char
            //     after that
            return !isMultiLine
                   || (extraCharCount > sourceConfig.getMaxCharactersToCompleteLine()
                       || isCurrCharALineBreak()
                       || currColNo == 1);
        }

        /**
         * Called for each decoded char found in the range (or just after it with line continuation)
         */
        private void markInsideRange() {
            optDecodeChar.ifPresent(decodedChar -> {
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

                // Need the prev location so when we test the first char after the range
                // we can get the last one in the range
                lastLineNo = currLineNo;
                lastColNo = currColNo;
            });
        }

        private void prepareForNextRead() {
            optDecodeChar.ifPresent(decodedChar -> {
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
                        clearCurrentLine();
                    }
                } else {
                    // This is a debatable one. Is a simple smiley emoji one col or two?
                    // Java will say the string has length 2
                    currColNo += decodedChar.getCharCount();
                }
            });
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


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
