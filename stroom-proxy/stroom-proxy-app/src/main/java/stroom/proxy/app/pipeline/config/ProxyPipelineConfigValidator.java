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

import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.queue.kafka.KafkaFileGroupQueue;
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
import stroom.proxy.app.pipeline.store.s3.S3FileStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    public static final String CODE_QUEUE_RESERVED_PROPERTY = "QUEUE_RESERVED_PROPERTY";
    public static final String CODE_STAGE_MISSING_INPUT_QUEUE = "STAGE_MISSING_INPUT_QUEUE";
    public static final String CODE_STAGE_MISSING_OUTPUT_QUEUE = "STAGE_MISSING_OUTPUT_QUEUE";
    public static final String CODE_STAGE_MISSING_FILE_STORE = "STAGE_MISSING_FILE_STORE";
    public static final String CODE_STAGE_UNKNOWN_INPUT_QUEUE = "STAGE_UNKNOWN_INPUT_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_OUTPUT_QUEUE = "STAGE_UNKNOWN_OUTPUT_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_SPLIT_ZIP_QUEUE = "STAGE_UNKNOWN_SPLIT_ZIP_QUEUE";
    public static final String CODE_STAGE_UNKNOWN_FILE_STORE = "STAGE_UNKNOWN_FILE_STORE";
    public static final String CODE_STAGE_INVALID_THREADS = "STAGE_INVALID_THREADS";
    public static final String CODE_STAGE_DISABLED = "STAGE_DISABLED";
    public static final String CODE_LOCAL_QUEUE_HAS_NO_CONSUMER = "LOCAL_QUEUE_HAS_NO_CONSUMER";
    public static final String CODE_STAGE_NOT_CONFIGURED = "STAGE_NOT_CONFIGURED";
    public static final String CODE_EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE =
            "EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE";
    public static final String CODE_S3_FILE_STORE_MISSING_BUCKET = "S3_FILE_STORE_MISSING_BUCKET";
    public static final String CODE_S3_FILE_STORE_MISSING_REGION = "S3_FILE_STORE_MISSING_REGION";
    public static final String CODE_S3_UNSUPPORTED_CREDENTIALS_TYPE = "S3_UNSUPPORTED_CREDENTIALS_TYPE";

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

    /**
     * Deployment-shape checks, applied when a whole proxy pipeline is assembled for a running process.
     * These are deliberately separate from {@link #validate(ProxyPipelineConfig)}, which answers whether
     * the configuration is structurally valid - a partial pipeline built directly from config (as tests
     * and embedded callers do) is structurally fine but is not a coherent deployment.
     */
    public PipelineValidationResult validateDeployment(final ProxyPipelineConfig pipelineConfig) {
        final List<PipelineValidationIssue> issues = new ArrayList<>();
        if (pipelineConfig != null) {
            validateNoUndrainedLocalQueues(pipelineConfig, issues);
        }
        return PipelineValidationResult.of(issues);
    }

    public void validateDeploymentOrThrow(final ProxyPipelineConfig pipelineConfig) {
        validateDeployment(pipelineConfig).throwIfInvalid();
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
                // Nothing to validate. QueueDefinition normalises a blank path to null on construction,
                // so getPath() is either null or non-blank and there is no invalid value to detect here.
            }
            case KAFKA -> {
                if (!queueDefinition.isKafkaConfigValid()) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_QUEUE_DEFINITION_INVALID,
                            "Kafka queue definitions must set both topic and bootstrapServers"));
                }
                validateReservedKafkaProperties(queueName, queueDefinition, issues);
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

    /**
     * Reject attempts to override Kafka properties the implementation depends on.
     * <p>
     * These are forced to their required values when the client is built, so an
     * override would otherwise be accepted and then quietly discarded. Each one also
     * fails invisibly if it did take effect - most sharply {@code max.poll.records},
     * where {@code next()} returns one record per poll and drops the rest of the
     * batch, silently skipping them until the consumer restarts or rebalances.
     * </p>
     */
    private void validateReservedKafkaProperties(final String queueName,
                                                 final QueueDefinition queueDefinition,
                                                 final List<PipelineValidationIssue> issues) {
        addReservedPropertyIssues(
                queueName,
                "consumer",
                queueDefinition.getConsumerConfig(),
                KafkaFileGroupQueue.RESERVED_CONSUMER_PROPERTIES,
                issues);

        addReservedPropertyIssues(
                queueName,
                "producer",
                queueDefinition.getProducerConfig(),
                KafkaFileGroupQueue.RESERVED_PRODUCER_PROPERTIES,
                issues);
    }

    private void addReservedPropertyIssues(final String queueName,
                                           final String blockName,
                                           final Map<String, String> suppliedConfig,
                                           final Set<String> reservedProperties,
                                           final List<PipelineValidationIssue> issues) {
        if (suppliedConfig == null || suppliedConfig.isEmpty()) {
            return;
        }

        suppliedConfig.keySet()
                .stream()
                .filter(reservedProperties::contains)
                .sorted()
                .forEach(property -> issues.add(PipelineValidationIssue.errorForQueue(
                        queueName,
                        CODE_QUEUE_RESERVED_PROPERTY,
                        "Kafka property '" + property + "' is set by the proxy and must not be "
                        + "overridden under '" + blockName + "' for queue '" + queueName
                        + "'. Reserved " + blockName + " properties: "
                        + reservedProperties.stream().sorted().collect(Collectors.joining(", ")))));
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

            // Reject an unrecognised credentials type rather than quietly falling back
            // to the default chain, which is what previously absorbed 'profile'. That
            // option was removed because per-store profiles are the wrong shape here -
            // identity belongs to the workload's IAM role - and because as implemented
            // it never selected a profile anyway.
            final String credentialsType = definition.getEffectiveCredentialsType();
            if (!S3FileStore.SUPPORTED_CREDENTIALS_TYPES.contains(credentialsType.toLowerCase())) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_S3_UNSUPPORTED_CREDENTIALS_TYPE,
                        "S3 file store '" + fileStoreName + "' has unsupported credentialsType '"
                        + credentialsType + "'. Supported types are: "
                        + S3FileStore.SUPPORTED_CREDENTIALS_TYPES.stream().sorted()
                                .collect(Collectors.joining(", "))
                        + ". To select a named AWS profile set AWS_PROFILE in the environment."));
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

        validateStagesAreFullySpecified(stages, issues);
        warnOnDisabledStages(stages, issues);
    }

    /**
     * Require an explicit {@code stages} block to name every stage.
     * <p>
     * Omission is ambiguous - a single-process proxy naming one stage to tune it
     * means "leave the rest alone", while a single-purpose node in a distributed
     * deployment means "run only this one". Either default is silently wrong for
     * the other case, so an incomplete block is rejected instead of guessed at.
     * Omitting the {@code stages} block entirely still gives the standard full
     * pipeline.
     * </p>
     */
    private void validateStagesAreFullySpecified(final PipelineStagesConfig stages,
                                                 final List<PipelineValidationIssue> issues) {
        final Set<PipelineStageName> configured = stages.getConfiguredStages();

        for (final PipelineStageName stageName : PipelineStageName.values()) {
            if (!configured.contains(stageName)) {
                issues.add(PipelineValidationIssue.errorForStage(
                        stageName,
                        CODE_STAGE_NOT_CONFIGURED,
                        "Stage '" + stageName.getConfigName() + "' is missing from the 'stages' "
                        + "block. When 'stages' is specified it must list all "
                        + PipelineStageName.values().length + " stages, each with an explicit "
                        + "'enabled'. Omit the 'stages' block entirely to get the standard "
                        + "full pipeline."));
            }
        }
    }

    /**
     * Report every disabled stage.
     * <p>
     * Disabling stages is legitimate - it is how work is split across processes -
     * so this is a warning, not an error. It makes each process's role explicit in
     * the startup log and shows which queues this process is not draining.
     * </p>
     */
    private void warnOnDisabledStages(final PipelineStagesConfig stages,
                                      final List<PipelineValidationIssue> issues) {
        addDisabledStageWarning(PipelineStageName.RECEIVE, stages.getReceive().isEnabled(), issues);
        addDisabledStageWarning(PipelineStageName.SPLIT_ZIP, stages.getSplitZip().isEnabled(), issues);
        addDisabledStageWarning(PipelineStageName.PRE_AGGREGATE, stages.getPreAggregate().isEnabled(), issues);
        addDisabledStageWarning(PipelineStageName.AGGREGATE, stages.getAggregate().isEnabled(), issues);
        addDisabledStageWarning(PipelineStageName.FORWARD, stages.getForward().isEnabled(), issues);
    }

    private void addDisabledStageWarning(final PipelineStageName stageName,
                                         final boolean enabled,
                                         final List<PipelineValidationIssue> issues) {
        if (!enabled) {
            issues.add(PipelineValidationIssue.warningForStage(
                    stageName,
                    CODE_STAGE_DISABLED,
                    "Stage '" + stageName.getConfigName() + "' is disabled in this process. "
                    + "Data will accumulate on its input queue unless another process "
                    + "is configured to consume it."));
        }
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
            // An S3 store is inherently shared - it addresses by bucket and has no
            // filesystem path - so a blank path says nothing about its shareability.
            // Warning on it flagged every correct S3 deployment and taught operators
            // to ignore this check.
            if (fileStoreDefinition != null && fileStoreDefinition.getType() == FileStoreType.S3) {
                return;
            }

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

    /**
     * A local filesystem queue is single-process by contract, so anything published to one that no
     * enabled stage in this process consumes is stranded until an operator intervenes. That is the
     * failure mode you get by disabling the aggregating stages without also re-pointing the receive
     * stage's output queue, and it is silent - the queue is not referenced by any enabled stage, so
     * it does not appear in the health check, the metrics or the monitor.
     * <p>
     * External queues are exempt: an SQS or Kafka queue is expected to be drained by another node.
     * </p>
     */
    private void validateNoUndrainedLocalQueues(final ProxyPipelineConfig pipelineConfig,
                                                final List<PipelineValidationIssue> issues) {
        final PipelineStagesConfig stages = pipelineConfig.getStages();
        if (stages == null) {
            return;
        }

        // Resolve exactly as ProxyPipelineAssembler does. Comparing the raw configured names meant a
        // config that omitted them - which is every config relying on defaults - was judged against
        // nulls rather than against the queues the assembler really publishes to and consumes from.
        final Set<String> consumed = new HashSet<>();
        addIfEnabled(consumed, stages.getSplitZip().isEnabled(),
                orDefault(stages.getSplitZip().getInputQueue(), ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE));
        addIfEnabled(consumed, stages.getPreAggregate().isEnabled(),
                orDefault(stages.getPreAggregate().getInputQueue(),
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE));
        addIfEnabled(consumed, stages.getAggregate().isEnabled(),
                orDefault(stages.getAggregate().getInputQueue(), ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE));
        addIfEnabled(consumed, stages.getForward().isEnabled(),
                orDefault(stages.getForward().getInputQueue(), ProxyPipelineConfig.FORWARDING_INPUT_QUEUE));

        final Map<String, String> published = new LinkedHashMap<>();
        putIfEnabled(published, stages.getReceive().isEnabled(),
                orDefault(stages.getReceive().getOutputQueue(),
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE), PipelineStageName.RECEIVE);
        putIfEnabled(published, stages.getReceive().isEnabled(),
                orDefault(stages.getReceive().getSplitZipQueue(),
                        stages.getSplitZip().isEnabled()
                                ? ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE
                                : null), PipelineStageName.RECEIVE);
        putIfEnabled(published, stages.getSplitZip().isEnabled(),
                orDefault(stages.getSplitZip().getOutputQueue(),
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE), PipelineStageName.SPLIT_ZIP);
        putIfEnabled(published, stages.getPreAggregate().isEnabled(),
                orDefault(stages.getPreAggregate().getOutputQueue(),
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE), PipelineStageName.PRE_AGGREGATE);
        putIfEnabled(published, stages.getAggregate().isEnabled(),
                orDefault(stages.getAggregate().getOutputQueue(),
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE), PipelineStageName.AGGREGATE);

        published.forEach((queueName, publisher) -> {
            if (consumed.contains(queueName)) {
                return;
            }
            final Map<String, QueueDefinition> queues = pipelineConfig.getQueues();
            final QueueDefinition definition = queues == null
                    ? null
                    : queues.get(queueName);
            final QueueType type = definition == null
                    ? QueueDefinition.DEFAULT_TYPE
                    : Objects.requireNonNullElse(definition.getType(), QueueDefinition.DEFAULT_TYPE);
            if (type != QueueType.LOCAL_FILESYSTEM) {
                // Another node is expected to drain it.
                return;
            }
            issues.add(PipelineValidationIssue.errorForQueue(
                    queueName,
                    CODE_LOCAL_QUEUE_HAS_NO_CONSUMER,
                    "The " + publisher + " stage publishes to local queue '" + queueName
                    + "' but no enabled stage consumes it, so data would be stranded there. "
                    + "Enable a stage that consumes it, re-point the publishing stage's output "
                    + "queue, or make the queue external so another node can drain it."));
        });
    }

    private static String orDefault(final String configured, final String defaultName) {
        return configured != null
                ? configured
                : defaultName;
    }

    private static void addIfEnabled(final Set<String> target,
                                     final boolean enabled,
                                     final String queueName) {
        if (enabled && queueName != null) {
            target.add(queueName);
        }
    }

    private static void putIfEnabled(final Map<String, String> target,
                                     final boolean enabled,
                                     final String queueName,
                                     final PipelineStageName publisher) {
        if (enabled && queueName != null) {
            target.putIfAbsent(queueName, publisher.getConfigName());
        }
    }

}
