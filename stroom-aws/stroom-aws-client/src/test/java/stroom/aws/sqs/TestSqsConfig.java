/*
 * Copyright 2026 Crown Copyright
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

package stroom.aws.sqs;

import stroom.aws.common.shared.AwsAssumeRole;
import stroom.aws.common.shared.AwsAssumeRoleClientConfig;
import stroom.aws.common.shared.AwsAssumeRoleRequest;
import stroom.aws.common.shared.AwsBasicCredentials;
import stroom.test.common.TestUtil;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

class TestSqsConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSqsConfig.class);

    @Test
    void testSerde() {
        final SqsConfig sqsConfig = SqsConfig.builder()
                .credentials(new AwsBasicCredentials("keyId", "secret"))
                .awsRegionName("us-east-1")
                .httpClient(HttpClientConfiguration.builder()
                        .timeout(StroomDuration.ofSeconds(10))
                        .build())
                .assumeRole(new AwsAssumeRole(
                        new AwsAssumeRoleClientConfig(
                                new AwsBasicCredentials("keyId", "secret"),
                                "us-east-1",
                                null),
                        new AwsAssumeRoleRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)))
                .queueUrl("queueURL")
                .visibilityTimeout(StroomDuration.ofSeconds(20))
                .maxNumberOfMessages(30)
                .pollMaxWaitTime(StroomDuration.ofSeconds(10))
                .deadLetterQueueUrl("DLQ")
                .build();

        final SqsConfig sqsConfig2 = TestUtil.testSerialisation(sqsConfig, SqsConfig.class);

        LOGGER.info("sqsConfig2: {}", sqsConfig2);
    }
}
