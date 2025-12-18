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
import stroom.cache.impl.CacheManagerImpl;
import stroom.data.shared.StreamTypeNames;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
class TestZstdSeekTableCacheImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZstdSeekTableCacheImpl.class);

    @Mock
    private MetaService mockMetaService;
    @Mock
    private S3Manager mockS3Manager;
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;

    @Test
    void test_unknownSize1() throws IOException {
        final ZstdSeekTableCacheImpl zstdSeekTableCache = new ZstdSeekTableCacheImpl(
                mockMetaService,
                mockS3Manager,
                new CacheManagerImpl());

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);

//        final AttributeMap attributeMap = new AttributeMap(Map.of(MetaFields.FILE_SIZE.getFldName(), "100"));
        final byte[] compressedData = makeData();

//        Mockito.when(mockMetaService.getAttributes(Mockito.any(Meta.class)))
//                .thenReturn(attributeMap);

        Mockito.when(mockS3Manager.getFileSize(Mockito.any(), Mockito.any()))
                .thenReturn((long) compressedData.length);

        Mockito.doAnswer(invocation -> {
            final Range<Long> range = invocation.getArgument(2, Range.class);
            final int from = Math.toIntExact(range.getFrom());
            final int to = Math.toIntExact(range.getTo());

            Mockito.when(mockResponseInputStream.readAllBytes())
                    .thenReturn(Arrays.copyOfRange(compressedData, from, to));

            return mockResponseInputStream;
        }).when(mockS3Manager).getByteRange(Mockito.any(Meta.class), Mockito.anyString(), Mockito.any());

        final ZstdSeekTable seekTable = zstdSeekTableCache.getSeekTable(meta, StreamTypeNames.META)
                .orElseThrow();
    }

    private byte[] makeData() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        try (final ZstdSegmentOutputStream zstdSegmentOutputStream =
                new ZstdSegmentOutputStream(byteArrayOutputStream)) {

            // Number of iterations need to be bigger than the speculative range size so the
            // first fetch doesn't get it all
            final int iterations = 2 * (ZstdSeekTableCacheImpl.STREAM_TYPE_TO_SPECULATIVE_SIZE_MAP.get(
                    StreamTypeNames.META) / ZstdConstants.SEEK_TABLE_ENTRY_SIZE);

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
