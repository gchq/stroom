/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.cache.impl.CacheManagerImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Range;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestZstdSeekTableCacheImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSeekTableCacheImpl.class);

    @Mock
    private MetaService mockMetaService;
    @Mock
    private S3Manager mockS3Manager;
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;
    @Mock
    private DataVolume mockDataVolume;

    @Test
    void test_unknownSize1() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

        // Number of iterations need to be bigger than the speculative range size so the
        // first fetch doesn't get it all
        final int size = ZstdSeekTableCacheImpl.STREAM_TYPE_TO_SPECULATIVE_SIZE_MAP.get(StreamTypeNames.META);
        final int iterations = 2 * (size / ZstdConstants.SEEK_TABLE_ENTRY_SIZE);

        final byte[] compressedData = makeData(iterations);

        Mockito.when(mockS3Manager.getFileSize(Mockito.any(), Mockito.any()))
                .thenReturn((long) compressedData.length);
        Mockito.when(mockDataVolume.getVolumeId())
                .thenReturn(123);

        final AtomicInteger fetchCount = new AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            fetchCount.incrementAndGet();
            final Range<Long> range = invocation.getArgument(2, Range.class);
            LOGGER.debug("range: {}", range);
            final int from = Math.toIntExact(range.getFrom());
            final int to = Math.toIntExact(range.getTo());

            Mockito.when(mockResponseInputStream.readAllBytes())
                    .thenReturn(Arrays.copyOfRange(compressedData, from, to));

            return mockResponseInputStream;
        }).when(mockS3Manager).getByteRange(Mockito.any(Meta.class), Mockito.anyString(), Mockito.any());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(
                        mockS3Manager,
                        mockDataVolume,
                        meta,
                        StreamTypeNames.META)
                .orElseThrow();

        assertThat(fetchCount)
                .hasValue(2);
        assertThat(seekTable.getFrameCount())
                .isEqualTo(iterations);
    }

    @Test
    void test_unknownSize2() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

        // Number of iterations need to be smaler han the speculative range size so the
        // first fetch includes it all
        final int size = ZstdSeekTableCacheImpl.STREAM_TYPE_TO_SPECULATIVE_SIZE_MAP.get(StreamTypeNames.META);
        final int iterations = (size / ZstdConstants.SEEK_TABLE_ENTRY_SIZE) / 2;

        final byte[] compressedData = makeData(iterations);

//        Mockito.when(mockMetaService.getAttributes(Mockito.any(Meta.class)))
//                .thenReturn(attributeMap);

        Mockito.when(mockS3Manager.getFileSize(Mockito.any(), Mockito.any()))
                .thenReturn((long) compressedData.length);
        Mockito.when(mockDataVolume.getVolumeId())
                .thenReturn(123);

        final AtomicInteger fetchCount = new AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            fetchCount.incrementAndGet();
            final Range<Long> range = invocation.getArgument(2, Range.class);
            LOGGER.debug("range: {}", range);
            final int from = Math.toIntExact(range.getFrom());
            final int to = Math.toIntExact(range.getTo());

            Mockito.when(mockResponseInputStream.readAllBytes())
                    .thenReturn(Arrays.copyOfRange(compressedData, from, to));

            return mockResponseInputStream;
        }).when(mockS3Manager).getByteRange(Mockito.any(Meta.class), Mockito.anyString(), Mockito.any());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(
                        mockS3Manager,
                        mockDataVolume,
                        meta,
                        StreamTypeNames.META)
                .orElseThrow();

        assertThat(fetchCount)
                .hasValue(1);

        assertThat(seekTable.getFrameCount())
                .isEqualTo(iterations);
    }

    @Test
    void test_unknownSize_unknownMeta() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

        Mockito.when(mockS3Manager.getFileSize(Mockito.any(), Mockito.any()))
                .thenThrow(NoSuchKeyException.builder()
                        .message("Key not found")
                        .build());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(
                        mockS3Manager,
                        mockDataVolume,
                        meta,
                        StreamTypeNames.META)
                .orElse(null);

        assertThat(seekTable)
                .isNull();
    }

    @Test
    void test_knownSize() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

        final int iterations = 10;
        final int frameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(iterations);

        final byte[] compressedData = makeData(iterations);

        final AtomicInteger fetchCount = new AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            fetchCount.incrementAndGet();
            final Range<Long> range = invocation.getArgument(2, Range.class);
            LOGGER.debug("range: {}", range);
            final int from = Math.toIntExact(range.getFrom());
            final int to = Math.toIntExact(range.getTo());

            Mockito.when(mockResponseInputStream.readAllBytes())
                    .thenReturn(Arrays.copyOfRange(compressedData, from, to));

            return mockResponseInputStream;
        }).when(mockS3Manager).getByteRange(Mockito.any(Meta.class), Mockito.anyString(), Mockito.any());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(
                        mockS3Manager,
                        mockDataVolume,
                        meta,
                        StreamTypeNames.META,
                        iterations, compressedData.length)
                .orElseThrow();

        assertThat(fetchCount)
                .hasValue(1);
        assertThat(seekTable.getFrameCount())
                .isEqualTo(iterations);
    }

    @Test
    void test_knownSize_unknownMeta() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

        final int iterations = 10;
        final byte[] compressedData = makeData(iterations);

        Mockito.when(mockS3Manager.getByteRange(Mockito.any(Meta.class), Mockito.anyString(), Mockito.any()))
                .thenThrow(NoSuchKeyException.builder()
                        .message("key not found")
                        .build());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(
                        mockS3Manager,
                        mockDataVolume,
                        meta,
                        StreamTypeNames.META,
                        iterations, compressedData.length)
                .orElse(null);

        assertThat(seekTable)
                .isNull();
    }

    private byte[] makeData(final int iterations) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        try (final ZstdSegmentOutputStream zstdSegmentOutputStream =
                new ZstdSegmentOutputStream(byteArrayOutputStream)) {

            LOGGER.debug("Using frame count: {}", iterations);

            for (int i = 0; i < iterations; i++) {
                if (i != 0) {
                    zstdSegmentOutputStream.addSegment();
                }
                zstdSegmentOutputStream.write(("Item-" + i).getBytes(StandardCharsets.UTF_8));
            }
        }
        final byte[] bytes = byteArrayOutputStream.toByteArray();
        LOGGER.debug("bytes.length: {}", bytes.length);
        return bytes;
    }
}
