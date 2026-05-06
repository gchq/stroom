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

package stroom.proxy.app.pipeline.queue.sqs;

import stroom.proxy.app.pipeline.queue.AbstractFileGroupQueueContractTest;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Runs the {@link AbstractFileGroupQueueContractTest} suite against a real
 * SQS queue provided by LocalStack via Testcontainers.
 * <p>
 * Tagged as "integration" so it is excluded from the normal {@code ./gradlew test}
 * run and only executed via {@code ./gradlew integrationTest}.
 * </p>
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class TestSqsFileGroupQueueContract extends AbstractFileGroupQueueContractTest {

    @Container
    static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.4"))
            .withServices(SQS);

    private SqsClient sqsClient;
    private String queueUrl;

    @BeforeEach
    void setUpSqs() {
        sqsClient = SqsClient.builder()
                .endpointOverride(LOCAL_STACK.getEndpointOverride(SQS))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                LOCAL_STACK.getAccessKey(),
                                LOCAL_STACK.getSecretKey())))
                .build();

        // Create a fresh queue for this test.
        queueUrl = sqsClient.createQueue(r -> r.queueName("contractTestQueue"))
                .queueUrl();
    }

    @Override
    protected FileGroupQueue createQueue(final String name) throws IOException {
        return new SqsFileGroupQueue(
                name,
                queueUrl,
                SqsFileGroupQueue.DEFAULT_VISIBILITY_TIMEOUT_SECONDS,
                0, // No long-poll wait in tests.
                sqsClient,
                new FileGroupQueueMessageCodec());
    }

    @AfterEach
    void tearDownSqs() {
        if (sqsClient != null && queueUrl != null) {
            try {
                sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
            } catch (final Exception e) {
                // Best-effort purge.
            }
        }
    }
}
