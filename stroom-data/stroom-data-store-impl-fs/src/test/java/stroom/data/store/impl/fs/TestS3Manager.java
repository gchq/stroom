package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.shared.AwsBasicCredentials;
import stroom.data.store.impl.fs.shared.AwsCredentialsProviderType;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TestS3Manager {

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
}
