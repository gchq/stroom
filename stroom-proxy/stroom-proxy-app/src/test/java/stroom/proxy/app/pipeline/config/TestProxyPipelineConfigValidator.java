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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.handler.ForwardRetryConfig;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.queue.kafka.KafkaFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.FanOutStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreType;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import java.util.List;
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
        // Receive straight to forward: the one local queue with a producer has a consumer.
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
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
                        new ReceiveStageConfig(
                                true,
                                null,
                                null,
                                null),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
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
                        new ReceiveStageConfig(
                                true,
                                "unknownQueue",
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .filteredOn(issue -> ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_OUTPUT_QUEUE.equals(issue.code()))
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("unknownQueue");
    }

    @Test
    void testReceiveStageRejectsUnknownSplitZipQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                "unknownSplitQueue",
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .filteredOn(issue ->
                        ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_SPLIT_ZIP_QUEUE.equals(issue.code()))
                .extracting(PipelineValidationIssue::queueName)
                .containsExactly("unknownSplitQueue");
    }

    @Test
    void testReceiveStageRejectsUnknownFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                "unknownStore"),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .filteredOn(issue -> ProxyPipelineConfigValidator.CODE_STAGE_UNKNOWN_FILE_STORE.equals(issue.code()))
                .extracting(PipelineValidationIssue::fileStoreName)
                .containsExactly("unknownStore");
    }

    @Test
    void testValidSeparatedLocalPipeline() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        new SplitZipStageConfig(
                                true,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_STORE,
                                new ConsumerStageThreadsConfig()),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                new AggregateStageThreadsConfig()),
                        new ForwardStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
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
                        disabledReceiveStage(),
                        new SplitZipStageConfig(
                                true,
                                null,
                                null,
                                null,
                                new ConsumerStageThreadsConfig()),
                        disabledAggregateStage(),
                        disabledForwardStage()),
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
                .filteredOn(issue -> issue.stageName() != null)
                .extracting(PipelineValidationIssue::stageName)
                .containsOnly(PipelineStageName.SPLIT_ZIP);
    }

    @Test
    void testAggregateStageRequiresInputOutputFileStoreThreadsAndBounds() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                null,
                                null,
                                null,
                                0,
                                "0",
                                StroomDuration.ZERO,
                                new AggregateStageThreadsConfig(0, 0)),
                        disabledForwardStage()),
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
        assertThat(result.getErrors())
                .filteredOn(issue -> ProxyPipelineConfigValidator.CODE_STAGE_INVALID_THREADS.equals(issue.code()))
                .as("consumerThreads and mergeThreads are each reported")
                .hasSize(2);
        assertThat(result.getErrors())
                .filteredOn(issue -> ProxyPipelineConfigValidator.CODE_AGGREGATE_BOUND_INVALID.equals(issue.code()))
                .as("every bound that is not positive is reported")
                .hasSize(3);
    }

    @Test
    void testAggregateStageMustStateEveryBound() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                null,
                                null,
                                null,
                                new AggregateStageThreadsConfig()),
                        new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(
                        ProxyPipelineConfigValidator.CODE_AGGREGATE_BOUND_NOT_STATED,
                        ProxyPipelineConfigValidator.CODE_AGGREGATE_BOUND_NOT_STATED,
                        ProxyPipelineConfigValidator.CODE_AGGREGATE_BOUND_NOT_STATED);
    }

    /**
     * The sweep deletes by age and cannot tell an orphan from live work, and R12 makes the loss
     * silent, so orphanAge must cover the wait and the one redelivery shared mode exists to survive:
     * a node dying mid-aggregate and another re-holding the input for a whole second window. Twice
     * the window is the bound; exactly twice is enough.
     */
    @Test
    void testASharedFilesystemStoresOrphanAgeMustBeAtLeastTwiceTheAggregationWindow() {
        assertThat(errorCodes(validator.validate(aggregatingSharedReceiveStore(StroomDuration.ofMinutes(5)))))
                .as("an input waits in the receive store for as long as its aggregate is open")
                .containsExactly(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_AGGREGATION_WINDOW);
        assertThat(errorCodes(validator.validate(aggregatingSharedReceiveStore(StroomDuration.ofMinutes(19)))))
                .as("one window plus a bit does not cover the re-hold after a node death")
                .containsExactly(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_AGGREGATION_WINDOW);
        assertThat(errorCodes(validator.validate(aggregatingSharedReceiveStore(StroomDuration.ofMinutes(20)))))
                .as("exactly twice is enough")
                .isEmpty();
    }

    private static ProxyPipelineConfig aggregatingSharedReceiveStore(final StroomDuration orphanAge) {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition(
                FileStoreType.SHARED_FILESYSTEM, "/mnt/shared/receive", orphanAge));
        return new ProxyPipelineConfig(
                PipelineMode.SHARED,
                allKafkaQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                1000,
                                "1G",
                                StroomDuration.ofMinutes(10),
                                new AggregateStageThreadsConfig()),
                        disabledForwardStage()),
                stores);
    }

    @Test
    void testMoreThanOneAggregateClaimerIsRefusedUnlessTheInputQueueIsKafka() {
        final ProxyPipelineConfig local = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                new AggregateStageThreadsConfig(2, 4)),
                        new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
                defaultFileStores());
        assertThat(errorCodes(validator.validate(local)))
                .containsExactly(ProxyPipelineConfigValidator.CODE_AGGREGATE_CLAIMERS_NEED_KAFKA);

        final ProxyPipelineConfig kafka = new ProxyPipelineConfig(
                PipelineMode.SHARED,
                allKafkaQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                new AggregateStageThreadsConfig(2, 4)),
                        disabledForwardStage()),
                allS3Stores());
        assertThat(validator.validate(kafka).isValid())
                .as("each Kafka claimer is a consumer with its own partitions")
                .isTrue();
    }

    @Test
    void testAggregateStageRequiresInputOutputAndFileStore() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                null,
                                null,
                                null,
                                new AggregateStageThreadsConfig()),
                        disabledForwardStage()),
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
                .filteredOn(issue -> issue.stageName() != null)
                .extracting(PipelineValidationIssue::stageName)
                .containsOnly(PipelineStageName.AGGREGATE);
    }

    @Test
    void testForwardStageRequiresInputQueue() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(
                                true,
                                null,
                                new ConsumerStageThreadsConfig())),
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
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(
                                true,
                                "unknownForwardingQueue",
                                new ConsumerStageThreadsConfig())),
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
                PipelineMode.SHARED,
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
                        null)),
                allStagesDisabled(),
                allS3Stores());

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
                PipelineMode.SHARED,
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
                        null)),
                allStagesDisabled(),
                allS3Stores());

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
    void testThrowIfInvalidThrowsPipelineValidationException() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(
                                true,
                                null,
                                new ConsumerStageThreadsConfig())),
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
                        new ReceiveStageConfig(
                                true,
                                null,
                                null,
                                null),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        assertThatThrownBy(() -> validator.validateOrThrow(config))
                .isInstanceOf(PipelineValidationException.class)
                .hasMessageContaining(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_OUTPUT_QUEUE)
                .hasMessageContaining(ProxyPipelineConfigValidator.CODE_STAGE_MISSING_FILE_STORE);
    }


    // --- Deployment-shape checks (validateDeployment) -------------------------------------------
    // These pin the guard that makes "turn aggregation off" fail loudly instead of stranding data
    // on a queue nothing drains.

    @Test
    void testDeploymentRejectsLocalQueueWithNoConsumer() {
        // Aggregation switched off the naive way: the two aggregating stages are disabled but receive
        // still publishes to aggregateInput, so every file group would be stranded there.
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, null)),
                defaultFileStores());

        final PipelineValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code)
                .containsExactly(ProxyPipelineConfigValidator.CODE_LOCAL_QUEUE_HAS_NO_CONSUMER);
    }

    @Test
    void testDeploymentAcceptsAggregationDisabledWhenReceiveTargetsTheForwardQueue() {
        // The supported way to run without aggregation, and the replacement for the removed
        // aggregator.enabled property: disable the aggregating stages AND split-zip, and re-point
        // receive at the forwarding queue.
        //
        // No splitZipQueue: the pipeline block is not merged with defaults, so an omitted property
        // is absent, and with nothing here to split a multi-feed zip is refused at receipt.
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, null)),
                defaultFileStores());

        assertThat(validator.validate(config).isValid()).isTrue();
    }

    @Test
    void testAStatedSplitZipQueueIsUsedAsStatedSoALocalOneNeedsAConsumerHere() {
        // Split-zip disabled here but the receive stage still names the queue: on a local queue nothing
        // would drain it, so it is refused; on a shared queue another node drains it, so it is fine.
        final PipelineStagesConfig stages = new PipelineStagesConfig(
                new ReceiveStageConfig(
                        true,
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                        ProxyPipelineConfig.RECEIVE_STORE),
                disabledSplitZipStage(),
                new AggregateStageConfig(true, ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, ProxyPipelineConfig.AGGREGATE_STORE,
                        1000, "1G", StroomDuration.ofMinutes(10), new AggregateStageThreadsConfig()),
                new ForwardStageConfig(true, ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, null));

        assertThat(errorCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.LOCAL, defaultQueues(), stages, defaultFileStores()))))
                .containsExactly(ProxyPipelineConfigValidator.CODE_LOCAL_QUEUE_HAS_NO_CONSUMER);
        assertThat(validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), stages, allS3Stores())).isValid())
                .as("the receive-here, split-there deployment")
                .isTrue();
    }

    @Test
    void testASharedFilesystemStoreMustStateItsPath() {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition(
                FileStoreType.SHARED_FILESYSTEM, null, StroomDuration.ofDays(7)));

        assertThat(errorCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), stores))))
                .containsExactly(ProxyPipelineConfigValidator.CODE_SHARED_FILE_STORE_REQUIRES_PATH);
    }

    @Test
    void testDeploymentAllowsAnExternalQueueWithNoLocalConsumer() {
        // A receive-only node in a shared deployment is legitimate: another process drains the
        // queue, so the undrained-queue guard is for local queues only.
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                PipelineMode.SHARED,
                allKafkaQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                allS3Stores());

        assertThat(validator.validate(config).isValid()).as(validator.validate(config).toString()).isTrue();
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        return Map.of(
                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition());
    }



    private static QueueDefinition kafkaQueue() {
        return new QueueDefinition(QueueType.KAFKA, null, "proxy-topic", "localhost:9092",
                null, null, null, null, null, null);
    }

    private static FileStoreDefinition s3Store(final String keyPrefix) {
        return new FileStoreDefinition(FileStoreType.S3, null, null, null, "eu-west-2", "a-bucket", keyPrefix,
                null, "default", null, null, null);
    }

    private static Map<String, QueueDefinition> allKafkaQueues() {
        return Map.of(
                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, kafkaQueue(),
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE, kafkaQueue(),
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, kafkaQueue());
    }

    private static Map<String, FileStoreDefinition> allS3Stores() {
        return Map.of(
                ProxyPipelineConfig.RECEIVE_STORE, s3Store("receive/"),
                ProxyPipelineConfig.SPLIT_STORE, s3Store("split/"),
                ProxyPipelineConfig.AGGREGATE_STORE, s3Store("aggregate/"));
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    @Test
    void testTheModeMustBeStated() {
        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                null, defaultQueues(), allStagesDisabled(), defaultFileStores()));

        assertThat(errorCodes(result)).containsExactly(ProxyPipelineConfigValidator.CODE_PIPELINE_MODE_NOT_STATED);
    }

    @Test
    void testASharedDeploymentAcceptsDistributedQueuesAndS3Stores() {
        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), allS3Stores()));

        assertThat(result.isValid()).as(result.toString()).isTrue();
    }

    @Test
    void testALocalQueueDoesNotFitASharedDeployment() {
        final Map<String, QueueDefinition> queues = new java.util.HashMap<>(allKafkaQueues());
        queues.put(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition());

        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, queues, allStagesDisabled(), allS3Stores()));

        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code, PipelineValidationIssue::queueName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        ProxyPipelineConfigValidator.CODE_QUEUE_TYPE_DOES_NOT_FIT_MODE,
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE));
    }

    @Test
    void testALocalStoreDoesNotFitASharedDeploymentWhateverItsPath() {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("/mnt/shared/receive"));

        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), stores));

        assertThat(result.getErrors())
                .extracting(PipelineValidationIssue::code, PipelineValidationIssue::fileStoreName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        ProxyPipelineConfigValidator.CODE_FILE_STORE_TYPE_DOES_NOT_FIT_MODE,
                        ProxyPipelineConfig.RECEIVE_STORE));
    }

    @Test
    void testADistributedQueueOrSharedStoreDoesNotFitALocalDeployment() {
        final Map<String, QueueDefinition> queues = new java.util.HashMap<>(defaultQueues());
        queues.put(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, kafkaQueue());
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(defaultFileStores());
        stores.put(ProxyPipelineConfig.AGGREGATE_STORE, s3Store("aggregate/"));

        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.LOCAL, queues, allStagesDisabled(), stores));

        assertThat(errorCodes(result)).containsExactlyInAnyOrder(
                ProxyPipelineConfigValidator.CODE_QUEUE_TYPE_DOES_NOT_FIT_MODE,
                ProxyPipelineConfigValidator.CODE_FILE_STORE_TYPE_DOES_NOT_FIT_MODE);
    }

    @Test
    void testASharedFilesystemStoreRequiresAnOrphanAge() {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.AGGREGATE_STORE,
                new FileStoreDefinition(FileStoreType.SHARED_FILESYSTEM, "/mnt/shared/aggregate", null));

        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), stores));

        assertThat(errorCodes(result))
                .containsExactly(ProxyPipelineConfigValidator.CODE_SHARED_FILE_STORE_REQUIRES_ORPHAN_AGE);

        stores.put(ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition(
                FileStoreType.SHARED_FILESYSTEM, "/mnt/shared/aggregate", StroomDuration.ofDays(7)));
        assertThat(validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), stores)).isValid()).isTrue();
    }

    /**
     * As for the aggregation window: the final attempt at maxRetryAge resolves the group and then
     * gives up, and the hourly sweep can run in between; twice the window covers it.
     */
    @Test
    void testTheOrphanAgeMustBeAtLeastTwiceTheForwardRetryWindow() {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition(
                FileStoreType.SHARED_FILESYSTEM, "/mnt/shared/aggregate", StroomDuration.ofDays(14)));
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), ProxyPipelineConfig.defaultFullPipelineStages(), stores);

        assertThat(errorCodes(validator.validate(config, List.of(destination(java.time.Duration.ofDays(14))), null)))
                .as("equal is not enough")
                .contains(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW);
        assertThat(errorCodes(validator.validate(config, List.of(destination(java.time.Duration.ofDays(8))), null)))
                .as("more than the window but less than twice it is not enough either")
                .contains(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW);
        assertThat(errorCodes(validator.validate(config, List.of(destination(java.time.Duration.ofDays(7))), null)))
                .as("exactly twice is enough")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW);
        assertThat(errorCodes(validator.validate(config)))
                .as("an unknown window cannot be checked")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW);
    }

    @Test
    void testMaxDeliveryAttemptsIsRefusedOnAnSqsQueue() {
        final Map<String, QueueDefinition> queues = new java.util.HashMap<>(allKafkaQueues());
        queues.put(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition(
                QueueType.SQS, null, null, null, null, null,
                "https://sqs.eu-west-2.amazonaws.com/123/q", null, null, 5));

        final PipelineValidationResult result = validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, queues, allStagesDisabled(), allS3Stores()));

        assertThat(errorCodes(result))
                .containsExactly(ProxyPipelineConfigValidator.CODE_SQS_MAX_DELIVERY_ATTEMPTS_NOT_APPLICABLE);
    }

    // --------------------------------------------------------------------------------
    // Forward destinations (designs/stages/forward.md §4.3, §4.4)

    @Test
    void testAnEnabledForwardStageNeedsADestination() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                PipelineMode.LOCAL, defaultQueues(), ProxyPipelineConfig.defaultFullPipelineStages(),
                defaultFileStores());

        assertThat(errorCodes(validator.validate(config, List.of(), null)))
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_NO_DESTINATION);
        assertThat(errorCodes(validator.validate(config, List.of(destination("a")), null)))
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_NO_DESTINATION);
        assertThat(errorCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.LOCAL, defaultQueues(), allStagesDisabled(), defaultFileStores()), List.of(), null)))
                .as("a node that does not forward needs no destination")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_NO_DESTINATION);
    }

    @Test
    void testSeveralDestinationsEachNeedTheirQueueAndStoreStated() {
        final ProxyPipelineConfig without = new ProxyPipelineConfig(
                PipelineMode.LOCAL, defaultQueues(), ProxyPipelineConfig.defaultFullPipelineStages(),
                defaultFileStores());
        final List<ForwardDestinationFacts> two = List.of(destination("a"), destination("b"));

        assertThat(errorCodes(validator.validate(without, two, null)))
                .containsExactlyInAnyOrder(
                        ProxyPipelineConfigValidator.CODE_FORWARD_DESTINATION_QUEUE_NOT_STATED,
                        ProxyPipelineConfigValidator.CODE_FORWARD_DESTINATION_QUEUE_NOT_STATED,
                        ProxyPipelineConfigValidator.CODE_FORWARD_DESTINATION_STORE_NOT_STATED,
                        ProxyPipelineConfigValidator.CODE_FORWARD_DESTINATION_STORE_NOT_STATED);

        final Map<String, QueueDefinition> queues = new java.util.HashMap<>(defaultQueues());
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(defaultFileStores());
        for (final String name : List.of("a", "b")) {
            queues.put(FanOutStage.queueNameFor(name), new QueueDefinition());
            stores.put(FanOutStage.storeNameFor(name), new FileStoreDefinition());
        }
        final ProxyPipelineConfig with = new ProxyPipelineConfig(
                PipelineMode.LOCAL, queues, ProxyPipelineConfig.defaultFullPipelineStages(), stores);
        assertThat(validator.validate(with, two, null).isValid()).isTrue();
        assertThat(validator.validate(without, List.of(destination("a")), null).isValid())
                .as("one destination fans out to nothing and needs no queue of its own")
                .isTrue();
    }

    @Test
    void testInSharedModeAGiveUpDirectoryMustBeUnderASharedStore() {
        final Map<String, FileStoreDefinition> stores = new java.util.HashMap<>(allS3Stores());
        stores.put(ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition(
                FileStoreType.SHARED_FILESYSTEM, "/mnt/shared/aggregate", StroomDuration.ofDays(30)));
        final ProxyPipelineConfig shared = new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), ProxyPipelineConfig.defaultFullPipelineStages(), stores);

        assertThat(errorCodes(validator.validate(shared,
                List.of(destination("a", java.nio.file.Path.of("/var/proxy/50_forwarding/a/03_failure"))), null)))
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
        assertThat(errorCodes(validator.validate(shared,
                List.of(destination("a", java.nio.file.Path.of("/mnt/shared/aggregate/give-up/a"))), null)))
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
        assertThat(errorCodes(validator.validate(shared,
                List.of(destination("a", java.nio.file.Path.of("/mnt/shared/aggregate/give-up"))), null)))
                .as("a direct child of the store's path is where the sweep looks for writer roots")
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
        assertThat(errorCodes(validator.validate(shared,
                List.of(destination("a", java.nio.file.Path.of("/mnt/shared/aggregate"))), null)))
                .as("the store's path itself")
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
        assertThat(errorCodes(validator.validate(shared, List.of(destination("a", null)), null)))
                .as("an S3 give-up is shared by nature")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
        assertThat(errorCodes(validator.validate(new ProxyPipelineConfig(
                        PipelineMode.LOCAL, defaultQueues(), ProxyPipelineConfig.defaultFullPipelineStages(),
                        defaultFileStores()),
                List.of(destination("a", java.nio.file.Path.of("/var/proxy/50_forwarding/a/03_failure"))), null)))
                .as("local mode: the node keeps its disk")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_GIVE_UP_NOT_SHARED);
    }

    @Test
    void testOnKafkaTheBackOffMustBeShorterThanThePollInterval() {
        final ProxyPipelineConfig kafka = new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), ProxyPipelineConfig.defaultFullPipelineStages(), allS3Stores());

        // The fact carries the longest wait the destination serves: with a growth factor of 1 that is
        // retryDelay whatever maxRetryDelay says, so the rule is checked against what is served.
        assertThat(errorCodes(validator.validate(kafka, List.of(new ForwardDestinationFacts(
                        "a", java.time.Duration.ofDays(7), java.time.Duration.ofMinutes(10),
                        java.time.Duration.ofHours(1), null)), null)))
                .as("an hour's wait is longer than the default 30 minute poll interval")
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL);
        assertThat(errorCodes(validator.validate(kafka, List.of(new ForwardDestinationFacts(
                        "a", java.time.Duration.ofDays(7), java.time.Duration.ofMinutes(10),
                        java.time.Duration.ofMinutes(10), null)), null)))
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL);
        assertThat(errorCodes(validator.validate(kafka, List.of(new ForwardDestinationFacts(
                        "a", java.time.Duration.ofDays(7), java.time.Duration.ofMinutes(45),
                        java.time.Duration.ofMinutes(45), null)), null)))
                .as("a flat 45 minute wait is served in full")
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL);
        assertThat(new ForwardRetryConfig(StroomDuration.ofDays(7), StroomDuration.ofMinutes(45), 1.0,
                StroomDuration.ofMinutes(10), null).toBounds().longestDelay())
                .as("the bounds report the served wait, not the unused cap")
                .isEqualTo(java.time.Duration.ofMinutes(45));
        assertThat(new ForwardRetryConfig().toBounds().longestDelay())
                .as("the defaults fit the default poll interval")
                .isLessThan(java.time.Duration.ofMillis(KafkaFileGroupQueue.DEFAULT_MAX_POLL_INTERVAL_MS));

        final Map<String, QueueDefinition> queues = new java.util.HashMap<>(allKafkaQueues());
        final QueueDefinition forward = queues.get(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
        queues.put(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition(
                QueueType.KAFKA, null, forward.getTopic(), forward.getBootstrapServers(), null,
                Map.of("max.poll.interval.ms", "not-a-number"), null, null, null, null));
        assertThat(errorCodes(validator.validate(new ProxyPipelineConfig(
                        PipelineMode.SHARED, queues, ProxyPipelineConfig.defaultFullPipelineStages(), allS3Stores()),
                List.of(destination("a")), null)))
                .as("a malformed interval is an issue, not a stack trace")
                .contains(ProxyPipelineConfigValidator.CODE_QUEUE_DEFINITION_INVALID);
    }

    @Test
    void testTheQueuesAttemptBoundFiringBeforeTheRetryAgeIsWarnedAbout() {
        final ProxyPipelineConfig local = new ProxyPipelineConfig(
                PipelineMode.LOCAL, defaultQueues(), ProxyPipelineConfig.defaultFullPipelineStages(),
                defaultFileStores());

        // 100 attempts, 10 minutes apart, end long before 7 days.
        final PipelineValidationResult result = validator.validate(local, List.of(destination("a")), null);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings().stream().map(PipelineValidationIssue::code))
                .contains(ProxyPipelineConfigValidator.CODE_FORWARD_ATTEMPTS_END_BEFORE_AGE);

        assertThat(validator.validate(local, List.of(new ForwardDestinationFacts(
                        "a", java.time.Duration.ofDays(7), java.time.Duration.ofHours(2),
                        java.time.Duration.ofHours(2), null)), null)
                .getWarnings().stream().map(PipelineValidationIssue::code))
                .doesNotContain(ProxyPipelineConfigValidator.CODE_FORWARD_ATTEMPTS_END_BEFORE_AGE);
    }

    /**
     * SQS keeps a message invisible for at most twelve hours from the receive, heartbeat or not. A
     * stage that holds a claim for longer has it redelivered under it and the work duplicated, so a
     * computable hold - the aggregation window, the longest forward back-off - is warned about from
     * half the ceiling, since what is added to it is not computable.
     */
    @Test
    void testAHoldNearTheSqsVisibilityCeilingIsWarnedAbout() {
        final Map<String, QueueDefinition> sqsQueues = Map.of(
                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, sqsQueue(),
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE, sqsQueue(),
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, sqsQueue());

        // The aggregation window.
        assertThat(warningCodes(validator.validate(aggregating(sqsQueues, StroomDuration.ofHours(7)))))
                .contains(ProxyPipelineConfigValidator.CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING);
        assertThat(warningCodes(validator.validate(aggregating(sqsQueues, StroomDuration.ofHours(6)))))
                .as("half the ceiling is the threshold")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING);
        assertThat(warningCodes(validator.validate(aggregating(allKafkaQueues(), StroomDuration.ofHours(7)))))
                .as("Kafka has no such ceiling")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING);

        // The forward back-off wait.
        final ProxyPipelineConfig forwarding = new ProxyPipelineConfig(
                PipelineMode.SHARED, sqsQueues, ProxyPipelineConfig.defaultFullPipelineStages(), allS3Stores());
        assertThat(warningCodes(validator.validate(forwarding, List.of(new ForwardDestinationFacts(
                        "a", java.time.Duration.ofDays(7), java.time.Duration.ofMinutes(10),
                        java.time.Duration.ofHours(8), null)), null)))
                .contains(ProxyPipelineConfigValidator.CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING);
        assertThat(warningCodes(validator.validate(forwarding, List.of(destination("a")), null)))
                .doesNotContain(ProxyPipelineConfigValidator.CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING);
    }

    /**
     * Event receipt is node-local in shared mode until events-shared.md is built; a node that
     * receives there is told so at boot (D7), and a node that does not receive is not.
     */
    @Test
    void testAReceivingNodeInSharedModeIsWarnedThatEventReceiptIsNodeLocal() {
        assertThat(warningCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), ProxyPipelineConfig.defaultFullPipelineStages(),
                allS3Stores()), List.of(destination("a")), null)))
                .contains(ProxyPipelineConfigValidator.CODE_EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE);
        assertThat(warningCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.SHARED, allKafkaQueues(), allStagesDisabled(), allS3Stores()))))
                .as("a node whose receive stage is disabled refuses events, so has no window")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE);
        assertThat(warningCodes(validator.validate(new ProxyPipelineConfig(
                PipelineMode.LOCAL, null, ProxyPipelineConfig.defaultFullPipelineStages(), null))))
                .as("local mode is the model the file appender was built for")
                .doesNotContain(ProxyPipelineConfigValidator.CODE_EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE);
    }

    private static ProxyPipelineConfig aggregating(final Map<String, QueueDefinition> queues,
                                                   final StroomDuration aggregationFrequency) {
        return new ProxyPipelineConfig(
                PipelineMode.SHARED,
                queues,
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                1000,
                                "1G",
                                aggregationFrequency,
                                new AggregateStageThreadsConfig()),
                        disabledForwardStage()),
                allS3Stores());
    }

    private static QueueDefinition sqsQueue() {
        return new QueueDefinition(QueueType.SQS, null, null, null, null, null,
                "https://sqs.eu-west-2.amazonaws.com/1/q", null, null, null);
    }

    private static java.util.List<String> warningCodes(final PipelineValidationResult result) {
        return result.getWarnings().stream().map(PipelineValidationIssue::code).toList();
    }

    private static ForwardDestinationFacts destination(final String name) {
        return destination(name, null);
    }

    private static ForwardDestinationFacts destination(final String name, final java.nio.file.Path giveUpDirectory) {
        return new ForwardDestinationFacts(
                name, java.time.Duration.ofDays(7), java.time.Duration.ofMinutes(10), java.time.Duration.ofHours(1),
                giveUpDirectory);
    }

    private static ForwardDestinationFacts destination(final java.time.Duration maxRetryAge) {
        return new ForwardDestinationFacts(
                "downstream", maxRetryAge, java.time.Duration.ofMinutes(10), java.time.Duration.ofHours(1), null);
    }

    private static java.util.List<String> errorCodes(final PipelineValidationResult result) {
        return result.getErrors().stream().map(PipelineValidationIssue::code).toList();
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        return Map.of(
                ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("stores/receive"),
                ProxyPipelineConfig.SPLIT_STORE, new FileStoreDefinition("stores/split"),
                ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition("stores/aggregate"));
    }

    /**
     * Stages default to enabled, so tests that exercise queue or file-store
     * validation in isolation disable them explicitly rather than relying on a
     * default.
     */
    private static PipelineStagesConfig allStagesDisabled() {
        return new PipelineStagesConfig(
                new ReceiveStageConfig(false, null, null, null),
                new SplitZipStageConfig(false, null, null, null, null),
                new AggregateStageConfig(false, null, null, null, null, null, null, null),
                new ForwardStageConfig(false, null, null));
    }



    private static ReceiveStageConfig disabledReceiveStage() {
        return new ReceiveStageConfig(false, null, null, null);
    }

    private static SplitZipStageConfig disabledSplitZipStage() {
        return new SplitZipStageConfig(false, null, null, null, null);
    }

    private static AggregateStageConfig disabledAggregateStage() {
        return new AggregateStageConfig(false, null, null, null, null, null, null, null);
    }

    private static ForwardStageConfig disabledForwardStage() {
        return new ForwardStageConfig(false, null, null);
    }
}
