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

package stroom.aws.s3.impl;

import stroom.aws.s3.impl.S3Manager.SegmentedMetaEntry;
import stroom.aws.s3.shared.AwsBasicCredentials;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.impl.CacheManagerImpl;
import stroom.cache.impl.TemplateCacheImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled // Needs minio running
public class TestS3Manager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestS3Manager.class);

    @Test
    void testHead(@TempDir final Path tempDir) throws IOException {

        final String uuid = UUID.randomUUID().toString();
        S3ClientConfig s3ClientConfig = getS3ClientConfig();
        s3ClientConfig = s3ClientConfig.copy()
                .keyPattern(s3ClientConfig.getKeyPattern() + "/" + uuid)
                .build();

        final S3Manager s3Manager = new S3Manager(
                new TemplateCacheImpl(new CacheManagerImpl()),
                s3ClientConfig,
                new S3MetaFieldsMapper(),
                new S3ClientPoolImpl(new CacheManagerImpl()));
        final Path file = tempDir.resolve("test.txt");

        Files.writeString(file,
                "This just some text to pad out the file",
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);

        final long size = Files.size(file);
        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);
        final AttributeMap attributeMap = new AttributeMap();

        try {
            final PutObjectResponse response = s3Manager.upload(meta, attributeMap, file);
            assertThat(response.size())
                    .isNull();  // Not present for a new upload

            final long fileSize = s3Manager.getFileSize(meta, null);
            assertThat(fileSize)
                    .isEqualTo(size);
        } finally {
            deleteFile(s3Manager, meta);
        }
    }

    @Test
    void getRange(@TempDir final Path tempDir) throws IOException {
        final String uuid = UUID.randomUUID().toString();
        S3ClientConfig s3ClientConfig = getS3ClientConfig();
        s3ClientConfig = s3ClientConfig.copy()
                .keyPattern(s3ClientConfig.getKeyPattern() + "/" + uuid)
                .build();

        final S3Manager s3Manager = new S3Manager(
                new TemplateCacheImpl(new CacheManagerImpl()),
                s3ClientConfig,
                new S3MetaFieldsMapper(),
                new S3ClientPoolImpl(new CacheManagerImpl()));
        final Path file = tempDir.resolve("test.txt");

        Files.writeString(file,
                "This just some text to pad out the file",
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);

        final long size = Files.size(file);
        final Meta meta = new Meta();
        meta.setId(123_456L);
        meta.setTypeName(StreamTypeNames.RAW_EVENTS);
        final AttributeMap attributeMap = new AttributeMap();

        try {
            final PutObjectResponse response = s3Manager.upload(meta, attributeMap, file);

            try (final ResponseInputStream<GetObjectResponse> response2 = s3Manager.getByteRange(
                    meta, null, Range.of(10L, 20L))) {

                final byte[] bytes = response2.readAllBytes();
                assertThat(bytes)
                        .hasSize(10);
                final String str = new String(bytes, StandardCharsets.UTF_8);
                assertThat(str)
                        .isEqualTo("some text ");
            }
        } finally {
            deleteFile(s3Manager, meta);
        }
    }

    @TestFactory
    Stream<DynamicTest> testBuildMetaEntry() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Entry<String, String>>() {
                })
                .withOutputType(SegmentedMetaEntry.class)
                .withSingleArgTestFunction(S3Manager::buildMetaEntry)
                .withSimpleEqualityAssertion()
                .addCase(Map.entry("foo", "bar"), null)
                .addCase(
                        Map.entry(S3Manager.AWS_USER_DEFINED_META_PREFIX + "foo", "bar"),
                        null)
                .addCase(
                        Map.entry(S3Manager.AWS_USER_DEFINED_META_PREFIX + "0-foo", "bar"),
                        null)
                .addCase(
                        Map.entry(S3Manager.AWS_USER_DEFINED_META_PREFIX
                                  + S3Manager.META_METADATA_KEY_PREFIX
                                  + "0-foo",
                                "bar"),
                        new SegmentedMetaEntry(0, "foo", "bar"))
                .addCase(
                        Map.entry(S3Manager.META_METADATA_KEY_PREFIX
                                  + "0-foo",
                                "bar"),
                        new SegmentedMetaEntry(0, "foo", "bar"))
                .addCase(
                        Map.entry(S3Manager.META_METADATA_KEY_PREFIX
                                  + "42-one two three",
                                "foo bar"),
                        new SegmentedMetaEntry(42, "one two three", "foo bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetIdPath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(S3Manager::getIdPath)
                .withSimpleEqualityAssertion()
                .addCase(0L, "")
                .addCase(1L, "")
                .addCase(999L, "")
                .addCase(4_321L, "004")
                .addCase(987_654_321L, "987/654")
                .addCase(999_999_999_001L, "999/999/999")
                .addCase(999_999_999_999L, "999/999/999")
                .build();
    }


    private static void deleteFile(final S3Manager s3Manager, final Meta meta) {
        try {
            s3Manager.delete(meta);
            LOGGER.debug("Deleted stream {}", meta.getId());
        } catch (final Exception e) {
            LOGGER.debug("Error deleting stream {}: {}", meta.getId(), LogUtil.exceptionMessage(e));
        }
    }


    //    @Test
//    void testUploadMultipart() throws IOException {
//        final S3Manager s3Manager = new S3Manager();
//        final Meta meta = Meta.builder()
//                .id((long) (Math.random() * Long.MAX_VALUE))
//                .createMs(0)
//                .effectiveMs(0L)
//                .feedName("TEST")
//                .typeName(StreamTypeNames.RAW_EVENTS)
//                .status(Status.UNLOCKED)
//                .build();
//        final Path path = Files.createTempFile("stroom", "test");
//        Files.writeString(path, "test");
//        s3Manager.uploadMultipart(meta, StreamTypeNames.RAW_EVENTS, new AttributeMap(), path, "test");
//
//    }
//
//    @Test
//    void testUploadAsync() throws IOException {
//        final S3Manager s3Manager = new S3Manager();
//        final Meta meta = Meta.builder()
//                .id((long) (Math.random() * Long.MAX_VALUE))
//                .createMs(0)
//                .effectiveMs(0L)
//                .feedName("TEST")
//                .typeName(StreamTypeNames.RAW_EVENTS)
//                .status(Status.UNLOCKED)
//                .build();
//        final Path path = Files.createTempFile("stroom", "test");
//        Files.writeString(path, "test");
//        s3Manager.uploadAsync(meta, StreamTypeNames.RAW_EVENTS, new AttributeMap(), path, "test");
//
//    }
//
//    @Test
//    void testUploadSync() throws IOException {
//        final S3Manager s3Manager = new S3Manager();
//        final S3ClientConfig s3ClientConfig = S3ClientConfig
//                .builder()
//                .credentialsProviderType(AwsCredentialsProviderType.STATIC)
//                .credentials(AwsBasicCredentials
//                        .builder()
//                        .accessKeyId("AKIAIOSFODNN7EXAMPLE")
//                        .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
//                        .build())
//                .region("us-west-2")
//                .endpointOverride("http://localhost:9444")
//                .build();
//        final S3Client s3Client = s3Manager.createClient(s3ClientConfig);
//        final Meta meta = Meta.builder()
//                .id((long) (Math.random() * Long.MAX_VALUE))
//                .createMs(0)
//                .effectiveMs(0L)
//                .feedName("TEST")
//                .typeName(StreamTypeNames.RAW_EVENTS)
//                .status(Status.UNLOCKED)
//                .build();
//        final String in = "test";
//        final Path dir = Files.createTempDirectory("stroom");
//        final Path source = dir.resolve("in");
//        Files.writeString(source, in);
//        s3Manager.uploadSync(s3Client, meta, StreamTypeNames.RAW_EVENTS, new AttributeMap(), source, "test");
//
//        final Path dest = dir.resolve("out");
//        s3Manager.downloadSync(s3Client, meta, StreamTypeNames.RAW_EVENTS, dest, "test");
//
//        final String out = Files.readString(dest);
//        assertThat(out).isEqualTo(in);
//    }

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
