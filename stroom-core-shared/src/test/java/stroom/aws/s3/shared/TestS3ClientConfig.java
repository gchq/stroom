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

package stroom.aws.s3.shared;

import stroom.aws.common.AwsBasicCredentials;
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
