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

package stroom.data.store.impl.fs.s3v2;


import stroom.aws.s3.impl.S3Manager;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.s3v2.ZstdSeekTable.InsufficientSeekTableDataException;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;

import jakarta.inject.Inject;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// TODO This may be too much data to put in to an in-mem cache as some of the seek tables could
//  be quite chunky. Maybe we need to cache to mmapped files
public class ZstdSeekTableCacheImpl implements ZstdSeekTableCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdSeekTableCacheImpl.class);
    // We could just get enough to read the frame count, then go back again with the right size but that is
    // always two requests to S3. Data transfer should be free to stroom so it doesn't matter if we get
    // more than we need, so hopefully in most cases we can get the table in one hit.
    private static final int SPECULATIVE_RANGE_SIZE = ZstdSegmentUtil.calculateSeekTableFrameSize(
            10_000);
    private static final int DEFAULT_SPECULATIVE_RANGE_SIZE = ZstdSegmentUtil.calculateSeekTableFrameSize(
            1_000);

    // Values are expected number of frames in a file. Only a very rough (ideally over) approximation.
    static final Map<String, Integer> STREAM_TYPE_TO_SPECULATIVE_SIZE_MAP = Map.of(
                    StreamTypeNames.META, 100,
                    StreamTypeNames.CONTEXT, 100,
                    StreamTypeNames.RAW_EVENTS, 100,
                    StreamTypeNames.RAW_REFERENCE, 100,
                    StreamTypeNames.ERROR, 10_000,
                    StreamTypeNames.EVENTS, 10_000,
                    StreamTypeNames.REFERENCE, 10_000,
                    StreamTypeNames.DETECTIONS, 10_000,
                    StreamTypeNames.RECORDS, 10_000)
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                    Entry::getKey,
                    entry -> ZstdSegmentUtil.calculateSeekTableFrameSize(entry.getValue())));

    public static final String CACHE_NAME = "Zstandard Seek Table Cache";

    private final MetaService metaService;
    private final S3Manager s3Manager;
    private final StroomCache<CacheKey, Optional<ZstdSeekTable>> cache;

    @Inject
    public ZstdSeekTableCacheImpl(final MetaService metaService,
                                  final S3Manager s3Manager,
                                  final CacheManager cacheManager) {
        this.metaService = metaService;
        this.s3Manager = s3Manager;
        this.cache = cacheManager.create(
                CACHE_NAME,
                () -> CacheConfig.builder()
                        .maximumSize(1_000)
                        .build());
    }

    @Override
    public Optional<ZstdSeekTable> getSeekTable(final Meta meta,
                                                final String childStreamType,
                                                final int segmentCount,
                                                final long fileSize) {
        final CacheKey cacheKey = makeKey(meta, childStreamType);
        return cache.get(cacheKey, ignored ->
                fetchSeekTable(meta, childStreamType, segmentCount, fileSize));
    }

    @Override
    public Optional<ZstdSeekTable> getSeekTable(final Meta meta, final String childStreamType) {
        final CacheKey cacheKey = makeKey(meta, childStreamType);
        return cache.get(cacheKey, ignored ->
                fetchSeekTable(meta, childStreamType));
    }

    @Override
    public void evict(final Meta meta, final String childStreamType) {
        final CacheKey cacheKey = makeKey(meta, childStreamType);
        cache.remove(cacheKey);
    }

    private CacheKey makeKey(final Meta meta, final String childStreamType) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(meta.getTypeName());
        return new CacheKey(meta.getId(), meta.getTypeName(), childStreamType);
    }

    private Optional<ZstdSeekTable> fetchSeekTable(final Meta meta, final String childStreamType) {
        LOGGER.debug("fetchSeekTable() - meta: {}", meta);

        // Need to know the file size because S3 range requests don't support negative ranges,
        // i.e. to get the last N bytes.
        final long fileSize = getFileSize(meta, childStreamType);

        // We don't know how many frames there are, so we don't know how big a chunk of the
        // end of the file we need to get the complete seek table. Therefore, we have to have a guess
        // and hope it is big enough. If it's not the data we get back will include the frame count
        // so we can make a 2nd request with the correct range.
        long rangeSize = getSpeculativeSeekTableFrameSize(meta, childStreamType, fileSize);
        boolean isSpeculativeRange = true;
        Range<Long> range;
        Optional<ZstdSeekTable> optZstdSeekTable;
        while (true) {
            range = ZstdSegmentUtil.getLastNRange(fileSize, rangeSize);
            try {
                // If we have a speculative range we want to copy the buffer as we may have fetched
                // way more data than we need, so don't want pointless bytes in our cache.
                optZstdSeekTable = fetchSeekTable(meta, childStreamType, range, isSpeculativeRange);
                break;
            } catch (final InsufficientSeekTableDataException e) {
                if (isSpeculativeRange) {
                    // We now know exactly how big the seek table frame is, so can try again with that
                    final long requiredSize = e.getRequiredSeekTableFrameSize();
                    LOGGER.debug(() -> LogUtil.message(
                            "fetchSeekTable() - Insufficient range, required: {}, received: {}",
                            ModelStringUtil.formatCsv(requiredSize),
                            ModelStringUtil.formatCsv(e.getActualSeekTableFrameSize())));
                    rangeSize = requiredSize;
                    isSpeculativeRange = false;
                } else {
                    // If we get in here then the file is not valid.
                    throw e;
                }
            }
        }
        if (optZstdSeekTable.isEmpty()) {
            throw new IllegalStateException(LogUtil.message(
                    "Expecting optZstdSeekTable to be present, meta: {}, childStreamType: {}", meta, childStreamType));
        }
        return optZstdSeekTable;
    }

    private int getSpeculativeSeekTableFrameSize(final Meta meta,
                                                 final String childStreamType,
                                                 final long fileSize) {
        final String streamTypeName = Objects.requireNonNullElseGet(childStreamType, meta::getTypeName);
        final Integer size = Objects.requireNonNullElse(
                STREAM_TYPE_TO_SPECULATIVE_SIZE_MAP.get(streamTypeName),
                DEFAULT_SPECULATIVE_RANGE_SIZE);
        LOGGER.debug("getActualSeekTableFrameSize() - streamTypeName: {}, size: {}", streamTypeName, size);
        if (size < fileSize) {
            return size;
        } else {
            return Math.toIntExact(fileSize);
        }
    }

    private long getFileSize(final Meta meta, final String childStreamType) {
        Long fileSize = null;
        // Attribute will only be present for the main stream, not the child ones.
        if (childStreamType == null) {
            // Try to get it from our DB first to save on S3 requests.
            final AttributeMap attributeMap = metaService.getAttributes(meta);
            final String val = attributeMap.get(MetaFields.FILE_SIZE.getFldName());
            if (NullSafe.isNonBlankString(val)) {
                try {
                    fileSize = Long.parseLong(val);
                } catch (final NumberFormatException e) {
                    LOGGER.debug("Unable to parse file size from '" + val + "'");
                    // Just swallow it as we can get it from the HEAD request
                }
            }
        }
        if (fileSize == null) {
            fileSize = s3Manager.getFileSize(meta, childStreamType);
        }
        return fileSize;
    }

    private Optional<ZstdSeekTable> fetchSeekTable(final Meta meta,
                                                   final String childStreamType,
                                                   final int segmentCount,
                                                   final long fileSize) {
        LOGGER.debug("fetchSeekTable() - meta: {}, segmentCount: {}, fileSize: {}", meta, segmentCount, fileSize);
        final Range<Long> frameRange = ZstdSegmentUtil.createSeekTableFrameRange(segmentCount, fileSize);
        // We have an exact size for the range fetch so don't copy the byte array returned.
        return fetchSeekTable(meta, childStreamType, frameRange, false);
    }

    private Optional<ZstdSeekTable> fetchSeekTable(final Meta meta,
                                                   final String childStreamType,
                                                   final Range<Long> range,
                                                   final boolean copyBufferContents) {
        LOGGER.debug("fetchSeekTable() - meta: {}, childStreamType: {}, range: {}", meta, childStreamType, range);
        final ResponseInputStream<GetObjectResponse> response = s3Manager.getByteRange(
                meta, childStreamType, range);
        try {
            final byte[] rangeBytes = response.readAllBytes();
            if (rangeBytes.length == 0) {
                throw new RuntimeException(LogUtil.message(
                        "No bytes returned from response, meta: {}, childStreamType: {} range: {}",
                        meta, childStreamType, range));
            }
            // We have fetched just the seek table frame so no point copying the entries part
            // (which is everything except 17 bytes)
            final Optional<ZstdSeekTable> zstdSeekTable = ZstdSeekTable.parse(
                    ByteBuffer.wrap(rangeBytes),
                    copyBufferContents);
            LOGGER.debug("fetchSeekTable() - returning: {}", zstdSeekTable);
            return zstdSeekTable;
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message(
                    "Error fetching range {} for meta {}, childStreamType: {} - {}",
                    range, meta, childStreamType, LogUtil.exceptionMessage(e)), e);
        }
    }


    // --------------------------------------------------------------------------------


    private record CacheKey(long metaId, String streamType, String childStreamType) {

    }
}
