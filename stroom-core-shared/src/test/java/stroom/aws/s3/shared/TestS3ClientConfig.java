package stroom.aws.s3.shared;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

class TestS3ClientConfig {

    @Test
    void testSerDeser() {
        final S3ClientConfig clientConfig = S3ClientConfig.builder()
                .accelerate(true)
                .async(true)
                .region("myRegion")
                .bucketName("myBucket")
                .credentials(AwsBasicCredentials.builder()
                        .accessKeyId("key")
                        .secretAccessKey("secret")
                        .build())
                .createBuckets(true)
                .keyPattern("keyPattern")
                .multipart(true)
                .numRetries(10)
                .thresholdInBytes(1234L)
                .targetThroughputInGbps(23D)
                .readBufferSizeInBytes(45L)
                .minimalPartSizeInBytes(68L)
                .maxConcurrency(2)
                .endpointOverride("endpointOverride")
                .checksumValidationEnabled(true)
                .forcePathStyle(true)
                .crossRegionAccessEnabled(true)
                .build();

        TestUtil.testSerialisation(clientConfig, S3ClientConfig.class);
    }
}
