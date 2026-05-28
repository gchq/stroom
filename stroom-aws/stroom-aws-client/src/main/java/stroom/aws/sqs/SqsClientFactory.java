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

package stroom.aws.sqs;


import stroom.aws.common.AwsCredentialsHelper;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Objects;

public class SqsClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqsClientFactory.class);

    public SqsClient createSqsClient(final SqsConfig sqsConfig) {
        Objects.requireNonNull(sqsConfig);

        final AwsCredentialsProvider awsCredentialsProvider = createCredentialsProvider(sqsConfig);

        final SqsClient sqsClient = SqsClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.of(sqsConfig.getAwsRegionName()))
                .build();

        LOGGER.debug("createSqsClient() - sqsConfig: {}", sqsClient);
        return sqsClient;
    }

    private AwsCredentialsProvider createCredentialsProvider(final SqsConfig sqsConfig) {
        return AwsCredentialsHelper.createCredentialsProvider(
                sqsConfig.getCredentials(),
                sqsConfig.getAssumeRole(),
                sqsConfig.getAwsRegionName());
    }
}
