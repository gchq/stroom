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

package stroom.proxy.app.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestProxyPipelineConfigValidator {

    private final ProxyPipelineConfigValidator validator = new ProxyPipelineConfigValidator();

    @Test
    void testDefaultConfigIsValid() {
        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig());

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasIssues()).isFalse();
        assertThat(result.getIssueCount()).isZero();
    }

    @Test
    void testNullConfigIsInvalid() {
        final PipelineValidationResult result = validator.validate(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly("PIPELINE_CONFIG_NULL");
    }

    @Test
    void testValidReceiveStage() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void testReceiveStageRequiresOutputQueueAndFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .contains(
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::stageName)
                .contains(PipelineStageName.RECEIVE);
    }

    @Test
    void testReceiveStageRejectsUnknownOutputQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                "unknownQueue",
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_OUTPUT_QUEUE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("unknownQueue");
    }

    @Test
    void testReceiveStageRejectsUnknownSplitZipQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                "unknownSplitQueue",
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_SPLIT_ZIP_QUEUE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("unknownSplitQueue");
    }

    @Test
    void testReceiveStageRejectsUnknownFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                null,
                                "unknownStore",
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_FILE_STORE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::fileStoreName)
                .containsExactly("unknownStore");
    }

    @Test
    void testValidSeparatedLocalPipeline() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig()),
                        new PipelineStageConfig(
                                true,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.SPLIT_STORE,
                                new PipelineStageThreadsConfig()),
                        new PipelineStageConfig(
                                true,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_STORE,
                                new PipelineStageThreadsConfig()),
                        new PipelineStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                new PipelineStageThreadsConfig()),
                        new PipelineStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig())),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void testSplitZipStageRequiresInputOutputAndFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .contains(
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_INPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::stageName)
                .containsOnly(PipelineStageName.SPLIT_ZIP);
    }

    @Test
    void testPreAggregateStageRequiresInputOutputFileStoreAndThreads() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        null,
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig(1, 0, 0)),
                        null,
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .contains(
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_INPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE,
                        ProxyPipelineConfigValidator.CODE_STAGE_INVALID_THREADS);
        assertThat(result.getErrors()
                .stream()
                .filter(issue -> ProxyPipelineConfigValidator.CODE_STAGE_INVALID_THREADS.equals(issue.code()))
                .count())
                .isEqualTo(2);
    }

    @Test
    void testAggregateStageRequiresInputOutputAndFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        null,
                        null,
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig()),
                        null),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .contains(
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_INPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE,
                        ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::stageName)
                .containsOnly(PipelineStageName.AGGREGATE);
    }

    @Test
    void testForwardStageRequiresInputQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        null,
                        null,
                        null,
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig())),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_INPUT_QUEUE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::stageName)
                .containsExactly(PipelineStageName.FORWARD);
    }

    @Test
    void testForwardStageRejectsUnknownInputQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        null,
                        null,
                        null,
                        new PipelineStageConfig(
                                true,
                                "unknownForwardingQueue",
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig())),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_INPUT_QUEUE);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("unknownForwardingQueue");
    }

    @Test
    void testKafkaQueueDefinitionRequiresTopicAndBootstrapServers() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("kafkaQueue", new QueueDefinition(
                        QueueType.KAFKA,
                        null,
                        null,
                        "localhost:9092",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                new PipelineStagesConfig(),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_QUEUE_DEFINITION_INVALID);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("kafkaQueue");
    }

    @Test
    void testSqsQueueDefinitionRequiresQueueUrl() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("sqsQueue", new QueueDefinition(
                        QueueType.SQS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                new PipelineStagesConfig(),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_QUEUE_DEFINITION_INVALID);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("sqsQueue");
    }

    @Test
    void testKinesisQueueDefinitionRequiresStreamNameAndApplicationName() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("kinesisQueue", new QueueDefinition(
                        QueueType.KINESIS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "proxy-stream",
                        null)),
                new PipelineStagesConfig(),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_QUEUE_DEFINITION_INVALID);
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("kinesisQueue");
    }

    @Test
    void testExternalQueueProducesSharedFileStoreWarningForUnspecifiedStorePath() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("kafkaQueue", new QueueDefinition(
                        QueueType.KAFKA,
                        null,
                        "proxy-topic",
                        "localhost:9092",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                new PipelineStagesConfig(),
                Map.of(ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition()));

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE);
        assertThat(result.getWarnings())
                .extracting(PipelineValidationIssue::fileStoreName)
                .containsExactly(ProxyPipelineConfig.RECEIVE_STORE);
    }

    @Test
    void testExternalQueueWithExplicitStorePathHasNoSharedStoreWarning() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("kafkaQueue", new QueueDefinition(
                        QueueType.KAFKA,
                        null,
                        "proxy-topic",
                        "localhost:9092",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                new PipelineStagesConfig(),
                Map.of(ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("/mnt/shared/receive")));

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void testThrowIfInvalidThrowsPipelineValidationException() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null,
                        null,
                        null,
                        null,
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig())),
                defaultFileStores());

        assertThatThrownBy(() -> validator.validate(config).throwIfInvalid())
                .isInstanceOf(PipelineValidationException.class)
                .hasMessageContaining(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_INPUT_QUEUE)
                .hasMessageContaining(PipelineStageName.FORWARD.getConfigName());
    }

    @Test
    void testValidateOrThrowThrowsPipelineValidationException() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                null,
                                null,
                                null,
                                new PipelineStageThreadsConfig()),
                        null,
                        null,
                        null,
                        null),
                defaultFileStores());

        assertThatThrownBy(() -> validator.validateOrThrow(config))
                .isInstanceOf(PipelineValidationException.class)
                .hasMessageContaining(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE)
                .hasMessageContaining(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE);
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        return Map.of(
                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition());
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        return Map.of(
                ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("stores/receive"),
                ProxyPipelineConfig.SPLIT_STORE, new FileStoreDefinition("stores/split"),
                ProxyPipelineConfig.PRE_AGGREGATE_STORE, new FileStoreDefinition("stores/pre-aggregate"),
                ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition("stores/aggregate"));
    }
}
