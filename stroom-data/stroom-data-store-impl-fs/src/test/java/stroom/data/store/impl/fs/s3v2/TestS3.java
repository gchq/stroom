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
import stroom.aws.s3.shared.AwsBasicCredentials;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.impl.CacheManagerImpl;
import stroom.cache.impl.TemplateCacheImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.SegmentOutputStream;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import net.datafaker.Faker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled // As it needs minio S3 to run.
public class TestS3 {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestS3.class);
    private static final int COMPRESSION_LEVEL = 7;
    private static final long RANDOM_SEED = 57294857573L;

    @Test
    void getRange(@TempDir final Path tempDir) throws IOException {
        final S3ClientConfig s3ClientConfig = getS3ClientConfig();

        final S3Manager s3Manager = new S3Manager(
                new TemplateCacheImpl(new CacheManagerImpl()),
                s3ClientConfig);

        final Path file = tempDir.resolve("test.txt");
        Files.writeString(file, """
                Stroom is an open-source, scalable big data platform developed by GCHQ for ingesting, \
                storing, processing, and analyzing massive volumes of event and log data. Its primary \
                function is to receive raw log files and use configurable Pipelines to transform and \
                normalize them into a structured format (usually XML). It provides fast, indexed search \
                capabilities across the stored data and tools for querying and visualization. It is \
                used by organizations needing centralized collection and analysis of billions of daily \
                security, performance, and usage events.
                """);

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);
        final AttributeMap attributeMap = new AttributeMap();

        try {
            s3Manager.delete(meta);
            LOGGER.debug("Deleted stream {}", meta.getId());
        } catch (final Exception e) {
            LOGGER.debug("Error deleting stream {}: {}", meta.getId(), LogUtil.exceptionMessage(e));
        }
        final PutObjectResponse response = s3Manager.upload(meta, attributeMap, file);
        final Long size = response.size();
        LOGGER.debug("Uploaded stream {}, size: {}", meta.getId(), size);

        final ResponseInputStream<GetObjectResponse> response2 = s3Manager.getByteRange(
                meta,
                null,
                Range.of(20L, 30L));
        LOGGER.debug("response2: {}", response2);

        final byte[] bytes = response2.readAllBytes();
        assertThat(bytes.length)
                .isEqualTo(10);
        final String output = new String(bytes, StandardCharsets.UTF_8);
        LOGGER.debug("output: {}", output);
        final Long contentLength = response2.response().contentLength();
        assertThat(contentLength)
                .isEqualTo(10);
    }

    @Test
    void testZstdGetRange(@TempDir final Path tempDir) throws IOException {

        final String uuid = UUID.randomUUID().toString();
        S3ClientConfig s3ClientConfig = getS3ClientConfig();
        s3ClientConfig = s3ClientConfig.copy()
                .keyPattern(s3ClientConfig.getKeyPattern() + "/" + uuid)
                .build();

        final S3Manager s3Manager = new S3Manager(
                new TemplateCacheImpl(new CacheManagerImpl()),
                s3ClientConfig);

        final int iterations = 10;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(iterations);
        final List<byte[]> dataBytes = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            TestZstdSegmentOutputStream.generateTestData(faker, i, data, dataBytes);
        }

        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                byteArrayOutputStream,
                null,
                COMPRESSION_LEVEL)) {

            TestZstdSegmentOutputStream.writeDataToStream(dataBytes, segmentOutputStream);
        }
        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        final Path file = tempDir.resolve("test.zst");

        Files.write(file, compressedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final long fileSize = Files.size(file);
        assertThat(fileSize)
                .isEqualTo(compressedBytes.length);

        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);
        final AttributeMap attributeMap = new AttributeMap();

        try {
            final PutObjectResponse response = s3Manager.upload(meta, attributeMap, file);
            final Long size = response.size();
            LOGGER.debug("Uploaded stream {}, size: {}", meta.getId(), size);

            final Range<Long> frameRange = ZstdSegmentUtil.createSeekTableFrameRange(iterations, fileSize);
            final int seekTableFrameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(iterations);
//            final long seekTableFramePosition = fileSize - seekTableFrameSize;
//            final Range<Long> range = Range.of(seekTableFramePosition, seekTableFramePosition + seekTableFrameSize);

            final ResponseInputStream<GetObjectResponse> response2 = s3Manager.getByteRange(
                    meta, null, frameRange);
            LOGGER.debug("response2: {}", response2);

            final byte[] rangeBytes = response2.readAllBytes();
            assertThat(rangeBytes.length)
                    .isEqualTo(seekTableFrameSize);

            final ZstdSeekTable zstdSeekTable = ZstdSeekTable.parse(ByteBuffer.wrap(rangeBytes))
                    .orElseThrow();
            assertThat(zstdSeekTable.getFrameCount())
                    .isEqualTo(iterations);

            final Long contentLength = response2.response().contentLength();
            assertThat(contentLength)
                    .isEqualTo(seekTableFrameSize);

        } finally {
            try {
                s3Manager.delete(meta);
                LOGGER.debug("Deleted stream {}", meta.getId());
            } catch (final Exception e) {
                LOGGER.debug("Error deleting stream {}: {}", meta.getId(), LogUtil.exceptionMessage(e));
            }
        }
    }

    private static S3ClientConfig getS3ClientConfig() {
        return S3ClientConfig
                .builder()
                .credentials(AwsBasicCredentials
                        .builder()
                        .accessKeyId("minioadmin")
                        .secretAccessKey("minioadmin")
                        .build())
                .region("us-east-1")
                .endpointOverride("http://127.0.0.1:9000")
                .multipart(true)
                .createBuckets(true)
                .bucketName("test-bucket")
                .keyPattern("${feed}/${type}/${year}/${idPadded}")
                .build();
    }
}
