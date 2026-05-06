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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validator for the reference-message proxy pipeline configuration.
 * <p>
 * This validator focuses on cross-field validation that is difficult to express
 * with bean validation annotations:
 * </p>
 * <ul>
 *     <li>enabled stages must reference the queues and file stores they need,</li>
 *     <li>referenced queue names must exist in {@link ProxyPipelineConfig#getQueues()},</li>
 *     <li>referenced file-store names must exist in {@link ProxyPipelineConfig#getFileStores()},</li>
 *     <li>queue definitions must contain the transport-specific settings required by their type, and</li>
 *     <li>enabled queue-consuming stages must have valid thread settings.</li>
 * </ul>
 */
public class ProxyPipelineConfigValidator {

    public static final String CODE_QUEUE_NAME_BLANK = "QUEUE_NAME_BLANK";
    public static final String CODE_FILE_STORE_NAME_BLANK = "FILE_STORE_NAME_BLANK";
    public static final String CODE_QUEUE_DEFINITION_NULL = "QUEUE_DEFINITION_NULL";
    public static final String CODE_FILE_STORE_DEFINITION_NULL = "FILE_STORE_DEFINITION_NULL";
    public static final String CODE_QUEUE_DEFINITION_INVALID = "QUEUE_DEFINITION_INVALID";
    public static final String CODE_STAGE_MISSING_INPUT_QUEUE = "STAGE_MISSING_INPUT_QUEUE";
    public static final String CODE_STAGE_MISSING_OUTPUT_QUEUE = "STAGE_MISSING_OUTPUT_QUEUE";
    public static final String CODE_STAGE_MISSING_SPLIT_ZIP_QUEUE = "STAGE_MISSING_SPLIT_ZIP_QUEUE";
    public static final String CODE_STAGE_MISSING_FILE_STORE = "STAGE_MISSING_FILE_STORE";
    public static final String CODE_STAGE_UNKNOWN_INPUT_QUEUE = "STAGE_UNKNOWN_INPUT_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_OUTPUT_QUEUE = "STAGE_UNKNOWN_OUTPUT_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_SPLIT_ZIP_QUEUE = "STAGE_UNKNOWN_SPLIT_ZIP_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_FILE_STORE = "STAGE_UNKNOWN_FILE_STORE";
    public static final String CODE_STAGE_INVALID_THREADS = "STAGE_INVALID_THREADS";
    public static final String CODE_LOCAL_QUEUE_PATH_BLANK = "LOCAL_QUEUE_PATH_BLANK";
    public static final String CODE_EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE =
            "EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE";
    public static final String CODE_S3_FILE_STORE_MISSING_BUCKET = "S3_FILE_STORE_MISSING_BUCKET";
    public static final String CODE_S3_FILE_STORE_MISSING_REGION = "S3_FILE_STORE_MISSING_REGION";

    public PipelineValidationResult validate(final ProxyPipelineConfig pipelineConfig) {
        final List<PipelineValidationIssue> issues = new ArrayList<>();

        if (pipelineConfig == null) {
            issues.add(PipelineValidationIssue.error(
                    "PIPELINE_CONFIG_NULL",
                    "Pipeline configuration must not be null"));
            return PipelineValidationResult.of(issues);
        }

        validateQueueDefinitions(pipelineConfig.getQueues(), issues);
        validateFileStoreDefinitions(pipelineConfig.getFileStores(), issues);
        validateStages(pipelineConfig, issues);
        validateExternalQueueFileStoreAssumptions(pipelineConfig, issues);

        return PipelineValidationResult.of(issues);
    }

    public void validateOrThrow(final ProxyPipelineConfig pipelineConfig) {
        validate(pipelineConfig).throwIfInvalid();
    }

    private void validateQueueDefinitions(final Map<String, QueueDefinition> queues,
                                          final List<PipelineValidationIssue> issues) {
        if (queues == null) {
            return;
        }

        queues.forEach((queueName, queueDefinition) -> {
            if (isBlank(queueName)) {
                issues.add(PipelineValidationIssue.error(
                        CODE_QUEUE_NAME_BLANK,
                        "Queue definition names must not be blank"));
                return;
            }

            if (queueDefinition == null) {
                issues.add(PipelineValidationIssue.errorForQueue(
                        queueName,
                        CODE_QUEUE_DEFINITION_NULL,
                        "Queue definition must not be null"));
                return;
            }

            validateQueueDefinition(queueName, queueDefinition, issues);
        });
    }

    private void validateQueueDefinition(final String queueName,
                                         final QueueDefinition queueDefinition,
                                         final List<PipelineValidationIssue> issues) {
        final QueueType type = Objects.requireNonNullElse(queueDefinition.getType(), QueueDefinition.DEFAULT_TYPE);

        switch (type) {
            case LOCAL_FILESYSTEM -> {
                if (queueDefinition.getPath() != null && queueDefinition.getPath().isBlank()) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_LOCAL_QUEUE_PATH_BLANK,
                            "Local filesystem queue path must not be blank if supplied"));
                }
            }
            case KAFKA -> {
                if (!queueDefinition.isKafkaConfigValid()) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_QUEUE_DEFINITION_INVALID,
                            "Kafka queue definitions must set both topic and bootstrapServers"));
                }
            }
            case SQS -> {
                if (!queueDefinition.isSqsConfigValid()) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_QUEUE_DEFINITION_INVALID,
                            "SQS queue definitions must set queueUrl"));
                }
            }
        }
    }

    private void validateFileStoreDefinitions(final Map<String, FileStoreDefinition> fileStores,
                                              final List<PipelineValidationIssue> issues) {
        if (fileStores == null) {
            return;
        }

        fileStores.forEach((fileStoreName, fileStoreDefinition) -> {
            if (isBlank(fileStoreName)) {
                issues.add(PipelineValidationIssue.error(
                        CODE_FILE_STORE_NAME_BLANK,
                        "File store definition names must not be blank"));
                return;
            }

            if (fileStoreDefinition == null) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_FILE_STORE_DEFINITION_NULL,
                        "File store definition must not be null"));
                return;
            }

            validateFileStoreDefinition(fileStoreName, fileStoreDefinition, issues);
        });
    }

    private void validateFileStoreDefinition(final String fileStoreName,
                                             final FileStoreDefinition definition,
                                             final List<PipelineValidationIssue> issues) {
        if (definition.getType() == FileStoreType.S3) {
            if (isBlank(definition.getBucket())) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_S3_FILE_STORE_MISSING_BUCKET,
                        "S3 file store '" + fileStoreName + "' must have a bucket"));
            }
            if (isBlank(definition.getRegion())) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_S3_FILE_STORE_MISSING_REGION,
                        "S3 file store '" + fileStoreName + "' must have a region"));
            }
        }
    }

    private void validateStages(final ProxyPipelineConfig pipelineConfig,
                                final List<PipelineValidationIssue> issues) {
        final PipelineStagesConfig stages = pipelineConfig.getStages();
        if (stages == null) {
            return;
        }

        validateReceiveStage(pipelineConfig, stages.getReceive(), issues);
        validateSplitZipStage(pipelineConfig, stages.getSplitZip(), issues);
        validatePreAggregateStage(pipelineConfig, stages.getPreAggregate(), issues);
        validateAggregateStage(pipelineConfig, stages.getAggregate(), issues);
        validateForwardStage(pipelineConfig, stages.getForward(), issues);
    }

    private void validateReceiveStage(final ProxyPipelineConfig pipelineConfig,
                                      final ReceiveStageConfig stage,
                                      final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredOutputQueue(
                pipelineConfig,
                PipelineStageName.RECEIVE,
                stage.getOutputQueue(),
                CODE_STAGE_MISSING_OUTPUT_QUEUE,
                CODE_STAGE_UNKNOWN_OUTPUT_QUEUE,
                issues);

        validateOptionalQueueReference(
                pipelineConfig,
                PipelineStageName.RECEIVE,
                stage.getSplitZipQueue(),
                CODE_STAGE_UNKNOWN_SPLIT_ZIP_QUEUE,
                issues);

        validateRequiredFileStore(
                pipelineConfig,
                PipelineStageName.RECEIVE,
                stage.getFileStore(),
                issues);

        validateReceiveThreads(PipelineStageName.RECEIVE, stage.getThreads(), issues);
    }

    private void validateSplitZipStage(final ProxyPipelineConfig pipelineConfig,
                                       final SplitZipStageConfig stage,
                                       final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.SPLIT_ZIP, stage.getInputQueue(), issues);
        validateRequiredOutputQueue(
                pipelineConfig,
                PipelineStageName.SPLIT_ZIP,
                stage.getOutputQueue(),
                CODE_STAGE_MISSING_OUTPUT_QUEUE,
                CODE_STAGE_UNKNOWN_OUTPUT_QUEUE,
                issues);
        validateRequiredFileStore(
                pipelineConfig,
                PipelineStageName.SPLIT_ZIP,
                stage.getFileStore(),
                issues);
        validateConsumerThreads(PipelineStageName.SPLIT_ZIP, stage.getThreads(), issues);
    }

    private void validatePreAggregateStage(final ProxyPipelineConfig pipelineConfig,
                                           final PreAggregateStageConfig stage,
                                           final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.PRE_AGGREGATE, stage.getInputQueue(), issues);
        validateRequiredOutputQueue(
                pipelineConfig,
                PipelineStageName.PRE_AGGREGATE,
                stage.getOutputQueue(),
                CODE_STAGE_MISSING_OUTPUT_QUEUE,
                CODE_STAGE_UNKNOWN_OUTPUT_QUEUE,
                issues);
        validateRequiredFileStore(
                pipelineConfig,
                PipelineStageName.PRE_AGGREGATE,
                stage.getFileStore(),
                issues);
        validateConsumerThreads(PipelineStageName.PRE_AGGREGATE, stage.getThreads(), issues);
        validateCloseOldAggregatesThreads(PipelineStageName.PRE_AGGREGATE, stage.getThreads(), issues);
    }

    private void validateAggregateStage(final ProxyPipelineConfig pipelineConfig,
                                        final AggregateStageConfig stage,
                                        final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.AGGREGATE, stage.getInputQueue(), issues);
        validateRequiredOutputQueue(
                pipelineConfig,
                PipelineStageName.AGGREGATE,
                stage.getOutputQueue(),
                CODE_STAGE_MISSING_OUTPUT_QUEUE,
                CODE_STAGE_UNKNOWN_OUTPUT_QUEUE,
                issues);
        validateRequiredFileStore(
                pipelineConfig,
                PipelineStageName.AGGREGATE,
                stage.getFileStore(),
                issues);
        validateConsumerThreads(PipelineStageName.AGGREGATE, stage.getThreads(), issues);
    }

    private void validateForwardStage(final ProxyPipelineConfig pipelineConfig,
                                      final ForwardStageConfig stage,
                                      final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.FORWARD, stage.getInputQueue(), issues);
        validateConsumerThreads(PipelineStageName.FORWARD, stage.getThreads(), issues);
    }

    private void validateRequiredInputQueue(final ProxyPipelineConfig pipelineConfig,
                                            final PipelineStageName stageName,
                                            final String queueName,
                                            final List<PipelineValidationIssue> issues) {
        validateRequiredQueueReference(
                pipelineConfig,
                stageName,
                queueName,
                CODE_STAGE_MISSING_INPUT_QUEUE,
                CODE_STAGE_UNKNOWN_INPUT_QUEUE,
                issues);
    }

    private void validateRequiredOutputQueue(final ProxyPipelineConfig pipelineConfig,
                                             final PipelineStageName stageName,
                                             final String queueName,
                                             final String missingCode,
                                             final String unknownCode,
                                             final List<PipelineValidationIssue> issues) {
        validateRequiredQueueReference(
                pipelineConfig,
                stageName,
                queueName,
                missingCode,
                unknownCode,
                issues);
    }

    private void validateRequiredQueueReference(final ProxyPipelineConfig pipelineConfig,
                                                final PipelineStageName stageName,
                                                final String queueName,
                                                final String missingCode,
                                                final String unknownCode,
                                                final List<PipelineValidationIssue> issues) {
        if (isBlank(queueName)) {
            issues.add(PipelineValidationIssue.errorForStage(
                    stageName,
                    missingCode,
                    "Enabled stage " + stageName.getConfigName() + " must reference a queue"));
            return;
        }

        validateOptionalQueueReference(pipelineConfig, stageName, queueName, unknownCode, issues);
    }

    private void validateOptionalQueueReference(final ProxyPipelineConfig pipelineConfig,
                                                final PipelineStageName stageName,
                                                final String queueName,
                                                final String unknownCode,
                                                final List<PipelineValidationIssue> issues) {
        if (isBlank(queueName)) {
            return;
        }

        if (!hasQueue(pipelineConfig, queueName)) {
            issues.add(PipelineValidationIssue.errorForStageQueue(
                    stageName,
                    queueName,
                    unknownCode,
                    "Stage " + stageName.getConfigName()
                    + " references queue " + queueName
                    + " but no such queue is configured"));
        }
    }

    private void validateRequiredFileStore(final ProxyPipelineConfig pipelineConfig,
                                           final PipelineStageName stageName,
                                           final String fileStoreName,
                                           final List<PipelineValidationIssue> issues) {
        if (isBlank(fileStoreName)) {
            issues.add(PipelineValidationIssue.errorForStage(
                    stageName,
                    CODE_STAGE_MISSING_FILE_STORE,
                    "Enabled stage " + stageName.getConfigName() + " must reference a file store"));
            return;
        }

        if (!hasFileStore(pipelineConfig, fileStoreName)) {
            issues.add(PipelineValidationIssue.errorForStageFileStore(
                    stageName,
                    fileStoreName,
                    CODE_STAGE_UNKNOWN_FILE_STORE,
                    "Stage " + stageName.getConfigName()
                    + " references file store " + fileStoreName
                    + " but no such file store is configured"));
        }
    }

    private void validateReceiveThreads(final PipelineStageName stageName,
                                        final ReceiveStageThreadsConfig threads,
                                        final List<PipelineValidationIssue> issues) {
        if (threads == null || threads.getMaxConcurrentReceives() < 1) {
            issues.add(PipelineValidationIssue.errorForStage(
                    stageName,
                    CODE_STAGE_INVALID_THREADS,
                    "Receive stage maxConcurrentReceives must be >= 1"));
        }
    }

    private void validateConsumerThreads(final PipelineStageName stageName,
                                         final ConsumerStageThreadsConfig threads,
                                         final List<PipelineValidationIssue> issues) {
        if (threads == null || threads.getConsumerThreads() < 1) {
            issues.add(PipelineValidationIssue.errorForStage(
                    stageName,
                    CODE_STAGE_INVALID_THREADS,
                    "Queue-consuming stage " + stageName.getConfigName() + " must have consumerThreads >= 1"));
        }
    }

    private void validateCloseOldAggregatesThreads(final PipelineStageName stageName,
                                                   final PreAggregateStageThreadsConfig threads,
                                                   final List<PipelineValidationIssue> issues) {
        if (threads == null || threads.getCloseOldAggregatesThreads() < 1) {
            issues.add(PipelineValidationIssue.errorForStage(
                    stageName,
                    CODE_STAGE_INVALID_THREADS,
                    "Pre-aggregate stage closeOldAggregatesThreads must be >= 1"));
        }
    }

    private void validateExternalQueueFileStoreAssumptions(final ProxyPipelineConfig pipelineConfig,
                                                           final List<PipelineValidationIssue> issues) {
        final Map<String, QueueDefinition> queues = pipelineConfig.getQueues();
        if (queues == null || queues.isEmpty()) {
            return;
        }

        final boolean hasExternalQueues = queues.values()
                .stream()
                .filter(Objects::nonNull)
                .map(queueDefinition -> Objects.requireNonNullElse(
                        queueDefinition.getType(),
                        QueueDefinition.DEFAULT_TYPE))
                .anyMatch(queueType -> queueType != QueueType.LOCAL_FILESYSTEM);

        if (!hasExternalQueues) {
            return;
        }

        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        if (fileStores == null || fileStores.isEmpty()) {
            issues.add(PipelineValidationIssue.warning(
                    CODE_EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE,
                    "External queues transport references only, so file stores must be shared between producers "
                    + "and consumers"));
            return;
        }

        fileStores.forEach((fileStoreName, fileStoreDefinition) -> {
            if (fileStoreDefinition == null || isBlank(fileStoreDefinition.getPath())) {
                issues.add(PipelineValidationIssue.warningForFileStore(
                        fileStoreName,
                        CODE_EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE,
                        "External queues transport references only. Ensure file store " + fileStoreName
                        + " uses shared storage visible to all producer and consumer processes"));
            }
        });
    }


    private static boolean hasQueue(final ProxyPipelineConfig pipelineConfig,
                                    final String queueName) {
        final Map<String, QueueDefinition> queues = pipelineConfig.getQueues();
        return queues != null && queues.containsKey(queueName);
    }

    private static boolean hasFileStore(final ProxyPipelineConfig pipelineConfig,
                                        final String fileStoreName) {
        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        return fileStores != null && fileStores.containsKey(fileStoreName);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
