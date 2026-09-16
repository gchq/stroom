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
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.FanOutStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreType;
import stroom.proxy.app.pipeline.store.s3.S3FileStore;
import stroom.util.io.PathCreator;
import stroom.util.time.StroomDuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one validator of the pipeline configuration ({@code designs/contracts.md} R14, R15): run once
 * at boot before anything is built, reporting every error it can find rather than the first, its
 * warnings logged, and the boot halted on any error. No other rule lives anywhere else - there are
 * no bean-validation annotations on the pipeline tree and no second check in the runtime.
 * <p>
 * What it checks: that the mode, queues, file stores and every stage are stated (explicit or fail);
 * that every queue and store fits the mode; that each enabled stage names queues and stores that
 * exist and has valid thread settings; that each queue definition carries what its type needs and
 * nothing its type forbids; that the forward destinations fit the pipeline block; and the bounds
 * that hold across layers - a shared store's {@code orphanAge} against the aggregation and retry
 * windows, a Kafka forward queue's poll interval against the back-off wait, an SQS hold against the
 * visibility ceiling.
 * </p>
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
    public static final String CODE_STAGE_ENABLED_NOT_STATED = "STAGE_ENABLED_NOT_STATED";
    public static final String CODE_STAGE_NOT_CONFIGURED = "STAGE_NOT_CONFIGURED";
    public static final String CODE_PIPELINE_MODE_NOT_STATED = "PIPELINE_MODE_NOT_STATED";
    public static final String CODE_QUEUE_TYPE_DOES_NOT_FIT_MODE = "QUEUE_TYPE_DOES_NOT_FIT_MODE";
    public static final String CODE_FILE_STORE_TYPE_DOES_NOT_FIT_MODE = "FILE_STORE_TYPE_DOES_NOT_FIT_MODE";
    public static final String CODE_SHARED_FILE_STORE_REQUIRES_ORPHAN_AGE = "SHARED_FILE_STORE_REQUIRES_ORPHAN_AGE";
    public static final String CODE_SHARED_FILE_STORE_REQUIRES_PATH = "SHARED_FILE_STORE_REQUIRES_PATH";
    public static final String CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW = "ORPHAN_AGE_WITHIN_RETRY_WINDOW";
    public static final String CODE_ORPHAN_AGE_WITHIN_AGGREGATION_WINDOW = "ORPHAN_AGE_WITHIN_AGGREGATION_WINDOW";
    public static final String CODE_S3_FILE_STORE_MISSING_BUCKET = "S3_FILE_STORE_MISSING_BUCKET";
    public static final String CODE_S3_FILE_STORE_MISSING_REGION = "S3_FILE_STORE_MISSING_REGION";
    public static final String CODE_S3_UNSUPPORTED_CREDENTIALS_TYPE = "S3_UNSUPPORTED_CREDENTIALS_TYPE";
    public static final String CODE_SQS_PROPERTY_OUT_OF_RANGE = "SQS_PROPERTY_OUT_OF_RANGE";
    public static final String CODE_SQS_MAX_DELIVERY_ATTEMPTS_NOT_APPLICABLE =
            "SQS_MAX_DELIVERY_ATTEMPTS_NOT_APPLICABLE";

    public static final String CODE_QUEUES_NOT_STATED = "QUEUES_NOT_STATED";
    public static final String CODE_AGGREGATE_BOUND_NOT_STATED = "AGGREGATE_BOUND_NOT_STATED";
    public static final String CODE_AGGREGATE_BOUND_INVALID = "AGGREGATE_BOUND_INVALID";
    public static final String CODE_AGGREGATE_CLAIMERS_NEED_KAFKA = "AGGREGATE_CLAIMERS_NEED_KAFKA";
    public static final String CODE_FILE_STORES_NOT_STATED = "FILE_STORES_NOT_STATED";
    public static final String CODE_FORWARD_NO_DESTINATION = "FORWARD_NO_DESTINATION";
    public static final String CODE_FORWARD_DESTINATION_QUEUE_NOT_STATED = "FORWARD_DESTINATION_QUEUE_NOT_STATED";
    public static final String CODE_FORWARD_DESTINATION_STORE_NOT_STATED = "FORWARD_DESTINATION_STORE_NOT_STATED";
    public static final String CODE_FORWARD_GIVE_UP_NOT_SHARED = "FORWARD_GIVE_UP_NOT_SHARED";
    public static final String CODE_FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL =
            "FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL";
    public static final String CODE_FORWARD_ATTEMPTS_END_BEFORE_AGE = "FORWARD_ATTEMPTS_END_BEFORE_AGE";
    public static final String CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING = "SQS_HOLD_NEAR_VISIBILITY_CEILING";
    public static final String CODE_EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE =
            "EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE";

    /**
     * SQS will not extend a message's visibility beyond this from the receive, whatever the heartbeat
     * asks for. A claim held longer is redelivered under its holder and the work duplicated.
     */
    static final Duration SQS_MAX_VISIBILITY = Duration.ofHours(12);
    /**
     * A computable hold - the aggregation window, a forward back-off wait - is warned about from
     * half the ceiling, since what is added to it (queue time before the claim, the delivery after
     * the wait) is not computable.
     */
    static final Duration SQS_HOLD_WARNING_THRESHOLD = SQS_MAX_VISIBILITY.dividedBy(2);

    /**
     * Validate the pipeline block alone, with the forward destinations unknown: the rules about
     * them are not applied.
     */
    public PipelineValidationResult validate(final ProxyPipelineConfig pipelineConfig) {
        return validate(pipelineConfig, null, null);
    }

    /**
     * The one validation of the pipeline configuration, run once at boot. It reports every error it
     * can find rather than the first, and the boot halts on any.
     *
     * @param forwardDestinations The enabled forward destinations, or null when they are not known,
     *                            as in a test of the pipeline block alone; empty means the forward
     *                            stage has nothing to deliver to. A shared filesystem store's
     *                            {@code orphanAge} must be at least twice the longest retry age among
     *                            them: the sweep deletes a group older than that whether or not
     *                            something still names it, and a group still being retried is named.
     * @param pathCreator         Resolves configured paths, so that a give-up directory can be
     *                            compared with a shared store's; null compares them as written.
     */
    public PipelineValidationResult validate(final ProxyPipelineConfig pipelineConfig,
                                             final List<ForwardDestinationFacts> forwardDestinations,
                                             final PathCreator pathCreator) {
        final List<PipelineValidationIssue> issues = new ArrayList<>();
        final Duration longestForwardRetryAge = forwardDestinations == null
                ? null
                : forwardDestinations.stream()
                        .map(ForwardDestinationFacts::maxRetryAge)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

        if (pipelineConfig == null) {
            issues.add(PipelineValidationIssue.error(
                    "PIPELINE_CONFIG_NULL",
                    "Pipeline configuration must not be null"));
            return PipelineValidationResult.of(issues);
        }

        validateStated(pipelineConfig, issues);
        validateQueueDefinitions(pipelineConfig.getQueues(), issues);
        validateFileStoreDefinitions(pipelineConfig.getFileStores(), issues);
        validateStages(pipelineConfig, issues);
        validateStagesStateEnabled(pipelineConfig.getStages(), issues);
        validateNoUndrainedLocalQueues(pipelineConfig, issues);
        validateMode(pipelineConfig, issues);
        validateOrphanAgeExceedsRetryWindow(pipelineConfig, longestForwardRetryAge, issues);
        validateOrphanAgeExceedsAggregationWindow(pipelineConfig, issues);
        validateForwardDestinations(pipelineConfig, forwardDestinations, pathCreator, issues);

        return PipelineValidationResult.of(issues);
    }

    public void validateOrThrow(final ProxyPipelineConfig pipelineConfig) {
        validate(pipelineConfig).throwIfInvalid();
    }

    /**
     * Explicit or fail: a pipeline is what the operator wrote. An absent or empty block is reported,
     * never filled in.
     */
    private void validateStated(final ProxyPipelineConfig pipelineConfig,
                                final List<PipelineValidationIssue> issues) {
        if (pipelineConfig.getQueues() == null || pipelineConfig.getQueues().isEmpty()) {
            issues.add(PipelineValidationIssue.error(
                    CODE_QUEUES_NOT_STATED,
                    "pipeline.queues must be stated and name every queue the stages use"));
        }
        if (pipelineConfig.getFileStores() == null || pipelineConfig.getFileStores().isEmpty()) {
            issues.add(PipelineValidationIssue.error(
                    CODE_FILE_STORES_NOT_STATED,
                    "pipeline.fileStores must be stated and name every file store the stages use"));
        }
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
        if (queueDefinition.isMaxDeliveryAttemptsStated() && queueDefinition.getMaxDeliveryAttempts() < 1) {
            issues.add(PipelineValidationIssue.errorForQueue(
                    queueName,
                    CODE_QUEUE_DEFINITION_INVALID,
                    "Queue '" + queueName + "' has maxDeliveryAttempts " + queueDefinition.getMaxDeliveryAttempts()
                    + "; it must be at least 1"));
        }
        final QueueType type = Objects.requireNonNullElse(queueDefinition.getType(), QueueDefinition.DEFAULT_TYPE);

        switch (type) {
            case LOCAL_FILESYSTEM -> {
                // Nothing to validate. QueueDefinition normalises a blank path to null on construction,
                // so getPath() is either null or non-blank and there is no invalid value to detect here.
            }
            case KAFKA -> {
                if (isBlank(queueDefinition.getTopic()) || isBlank(queueDefinition.getBootstrapServers())) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_QUEUE_DEFINITION_INVALID,
                            "Kafka queue definitions must set both topic and bootstrapServers"));
                }
                validateReservedKafkaProperties(queueName, queueDefinition, issues);
            }
            case SQS -> {
                validateSqsBounds(queueName, queueDefinition, issues);
                validateSqsGiveUpIsTheRedrivePolicy(queueName, queueDefinition, issues);
                if (isBlank(queueDefinition.getQueueUrl())) {
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
        if (definition.getType() == FileStoreType.SHARED_FILESYSTEM) {
            if (isBlank(definition.getPath())) {
                // Without a path the store would be derived under this node's data directory, which
                // is the one place a shared store must not be: every other node would fail to resolve
                // what this one wrote.
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_SHARED_FILE_STORE_REQUIRES_PATH,
                        "Shared filesystem file store '" + fileStoreName + "' must set a path on the mount every "
                        + "node sees. Without one the store is derived under this node's own data directory."));
            }
            final StroomDuration orphanAge = definition.getOrphanAge();
            if (orphanAge == null || orphanAge.isZero() || orphanAge.getDuration().isNegative()) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_SHARED_FILE_STORE_REQUIRES_ORPHAN_AGE,
                        "Shared filesystem file store '" + fileStoreName + "' must set a positive orphanAge. "
                        + "A node that leaves residue on a shared mount may never start again, so the "
                        + "proxy clears it by age, and the age must be stated."));
            }
        }
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

            // An unrecognised credentials type is refused rather than quietly falling back to
            // the default chain. There is deliberately no 'profile' type: identity belongs to the
            // workload's IAM role, and an operator who wants a profile sets AWS_PROFILE, which the
            // default chain honours.
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
     * the other case, so an incomplete block is rejected instead of guessed at,
     * and an omitted block is reported as every stage unconfigured.
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
                        + "block. Every one of the " + PipelineStageName.values().length + " stages "
                        + "must be listed, each stating 'enabled' explicitly, so that what this "
                        + "process runs is exactly what was written down."));
            }
        }
    }

    /**
     * Require every named stage to say whether it is enabled (R5).
     * <p>
     * A block written to tune one setting must not switch the stage on or off by implication.
     * There is no reading of an absent {@code enabled} that is right more than half the time, so it
     * is an error.
     * </p>
     */
    private void validateStagesStateEnabled(final PipelineStagesConfig stages,
                                            final List<PipelineValidationIssue> issues) {
        final Set<PipelineStageName> configured = stages.getConfiguredStages();

        record Stage(PipelineStageName name, boolean specified) {

        }

        final List<Stage> checks = List.of(
                new Stage(PipelineStageName.RECEIVE, stages.getReceive().isEnabledSpecified()),
                new Stage(PipelineStageName.SPLIT_ZIP, stages.getSplitZip().isEnabledSpecified()),
                new Stage(PipelineStageName.AGGREGATE, stages.getAggregate().isEnabledSpecified()),
                new Stage(PipelineStageName.FORWARD, stages.getForward().isEnabledSpecified()));

        for (final Stage stage : checks) {
            // Only complain about a stage that is actually present; a missing one is already an error.
            if (configured.contains(stage.name()) && !stage.specified()) {
                issues.add(PipelineValidationIssue.errorForStage(
                        stage.name(),
                        CODE_STAGE_ENABLED_NOT_STATED,
                        "Stage '" + stage.name().getConfigName() + "' does not say whether it is "
                        + "enabled. Every stage must state 'enabled' explicitly."));
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

        // Event receipt appends to files on this node's disk and acknowledges the sender on that,
        // which is the local single-owner model; in shared mode a node removed with open or
        // unreceived event files takes acknowledged events with it. The shared-mode path - a topic
        // as the queue - is designed (designs/infrastructure/events-shared.md) and not built. Until
        // it is, this is a stated window rather than a refusal: an error would take event receipt
        // away from shared deployments with nothing to offer instead.
        if (pipelineConfig.getMode() == PipelineMode.SHARED) {
            issues.add(PipelineValidationIssue.warningForStage(
                    PipelineStageName.RECEIVE,
                    CODE_EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE,
                    "This node receives in SHARED mode, and single-event receipt (the event resource and any "
                    + "SQS connector) still batches events into files on this node's disk before they enter the "
                    + "pipeline. A node removed with open or unreceived event files loses up to eventStore.maxAge "
                    + "of acknowledged events per feed, plus anything in event/failed. Posted files and zips are "
                    + "not affected. The shared-mode event path is designed and not yet built "
                    + "(designs/infrastructure/events-shared.md)."));
        }

        validateRequiredFileStore(
                pipelineConfig,
                PipelineStageName.RECEIVE,
                stage.getFileStore(),
                issues);
    }

    private void validateSplitZipStage(final ProxyPipelineConfig pipelineConfig,
                                       final SplitZipStageConfig stage,
                                       final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.SPLIT_ZIP,
                stage.getInputQueue(), issues);
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

    /**
     * On SQS the bound on delivery is the queue's redrive policy and the dead-letter queue is where a
     * human acts; the proxy counts nothing. A maxDeliveryAttempts on an SQS queue would be read as
     * doing something, so it is refused.
     */
    private void validateSqsGiveUpIsTheRedrivePolicy(final String queueName,
                                                     final QueueDefinition definition,
                                                     final List<PipelineValidationIssue> issues) {
        if (definition.getType() == QueueType.SQS && definition.isMaxDeliveryAttemptsStated()) {
            issues.add(PipelineValidationIssue.errorForQueue(
                    queueName,
                    CODE_SQS_MAX_DELIVERY_ATTEMPTS_NOT_APPLICABLE,
                    "SQS queue '" + queueName + "' sets maxDeliveryAttempts, which does not apply to SQS: give-up "
                    + "is the queue's redrive policy (maxReceiveCount) and its dead-letter queue. Remove it and "
                    + "configure the redrive policy on the queue."));
        }
    }

    /**
     * SQS accepts a visibility timeout of 0-43200 seconds and a receive wait of 0-20, and rejects
     * every call made with a value outside them - so a queue configured with, say, a wait time in
     * minutes would validate and then fail on every poll. Under R9 that is a boot failure, checked
     * here in seconds, which is also what {@code SqsFileGroupQueue} truncates the durations to.
     */
    private void validateSqsBounds(final String queueName,
                                   final QueueDefinition definition,
                                   final List<PipelineValidationIssue> issues) {
        checkSecondsBound(queueName, "visibilityTimeout",
                definition.getVisibilityTimeout(), 0, 43_200, issues);
        checkSecondsBound(queueName, "waitTime",
                definition.getWaitTime(), 0, 20, issues);
    }

    private void checkSecondsBound(final String queueName,
                                   final String propertyName,
                                   final stroom.util.time.StroomDuration value,
                                   final long min,
                                   final long max,
                                   final List<PipelineValidationIssue> issues) {
        if (value == null) {
            return;
        }
        final long seconds = value.getDuration().toSeconds();
        if (seconds < min || seconds > max) {
            issues.add(PipelineValidationIssue.errorForQueue(
                    queueName,
                    CODE_SQS_PROPERTY_OUT_OF_RANGE,
                    "SQS queue '" + queueName + "' has " + propertyName + " of " + seconds
                    + "s, which is outside the range SQS accepts (" + min + "-" + max + "s). "
                    + "Every call to the queue would be rejected."));
        }
    }

    private void validateAggregateStage(final ProxyPipelineConfig pipelineConfig,
                                        final AggregateStageConfig stage,
                                        final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.AGGREGATE,
                stage.getInputQueue(), issues);
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
        validateAggregateThreads(pipelineConfig, stage, issues);
        validateAggregateBounds(stage, issues);
        validateAggregateHoldFitsSqs(pipelineConfig, stage, issues);
    }

    /**
     * One claimer holds every open aggregate on the node, so a feed's inputs land in one aggregate.
     * A second claimer on SQS or on disk would only spread a feed across two; on Kafka each claimer
     * is a consumer with its own partitions and a feed still reaches one of them. Merge workers are
     * a throughput setting and only need to exist.
     */
    private void validateAggregateThreads(final ProxyPipelineConfig pipelineConfig,
                                          final AggregateStageConfig stage,
                                          final List<PipelineValidationIssue> issues) {
        final AggregateStageThreadsConfig threads = stage.getThreads();
        if (threads == null) {
            return;
        }
        if (threads.getMergeThreads() < 1) {
            issues.add(PipelineValidationIssue.errorForStage(
                    PipelineStageName.AGGREGATE,
                    CODE_STAGE_INVALID_THREADS,
                    "Stage aggregate must have mergeThreads >= 1"));
        }
        if (threads.getConsumerThreads() > 1) {
            final String inputQueue = orDefault(stage.getInputQueue(), ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
            final QueueDefinition definition = pipelineConfig.getQueues() == null
                    ? null
                    : pipelineConfig.getQueues().get(inputQueue);
            final QueueType type = definition == null
                    ? QueueDefinition.DEFAULT_TYPE
                    : Objects.requireNonNullElse(definition.getType(), QueueDefinition.DEFAULT_TYPE);
            if (type != QueueType.KAFKA) {
                issues.add(PipelineValidationIssue.errorForStage(
                        PipelineStageName.AGGREGATE,
                        CODE_AGGREGATE_CLAIMERS_NEED_KAFKA,
                        "Stage aggregate has consumerThreads " + threads.getConsumerThreads() + " but its input "
                        + "queue '" + inputQueue + "' is " + type + ". Each claimer thread holds its own "
                        + "aggregates, so on anything but Kafka a second one only spreads a feed across two "
                        + "smaller aggregates. Set consumerThreads: 1 and raise mergeThreads instead."));
            }
        }
    }

    /**
     * An input is held claimed for the whole aggregation window; on SQS that hold has a ceiling.
     */
    private static void validateAggregateHoldFitsSqs(final ProxyPipelineConfig pipelineConfig,
                                                     final AggregateStageConfig stage,
                                                     final List<PipelineValidationIssue> issues) {
        final String inputQueue = orDefault(stage.getInputQueue(), ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
        if (stage.getAggregationFrequency() != null && isSqs(pipelineConfig, inputQueue)) {
            warnIfNearSqsVisibilityCeiling(PipelineStageName.AGGREGATE, inputQueue, "aggregationFrequency",
                    stage.getAggregationFrequency().getDuration(), issues);
        }
    }

    private static boolean isSqs(final ProxyPipelineConfig pipelineConfig, final String queueName) {
        final QueueDefinition definition = pipelineConfig.getQueues() == null
                ? null
                : pipelineConfig.getQueues().get(queueName);
        return definition != null
               && Objects.requireNonNullElse(definition.getType(), QueueDefinition.DEFAULT_TYPE) == QueueType.SQS;
    }

    /**
     * The visibility heartbeat keeps a held SQS message invisible, but SQS caps the total at
     * {@link #SQS_MAX_VISIBILITY} from the receive. A stage that holds a claim for longer has it
     * redelivered under it - duplicates, not loss - so a computable hold anywhere near the ceiling
     * is worth a warning; what is added to it is not computable.
     */
    private static void warnIfNearSqsVisibilityCeiling(final PipelineStageName stageName,
                                                       final String queueName,
                                                       final String propertyName,
                                                       final Duration hold,
                                                       final List<PipelineValidationIssue> issues) {
        if (hold.compareTo(SQS_HOLD_WARNING_THRESHOLD) > 0) {
            issues.add(PipelineValidationIssue.warningForStage(
                    stageName,
                    CODE_SQS_HOLD_NEAR_VISIBILITY_CEILING,
                    "Stage " + stageName.getConfigName() + " holds a claim on SQS queue '" + queueName + "' for up to "
                    + propertyName + " " + hold + ", and SQS will not keep a message invisible for more than "
                    + SQS_MAX_VISIBILITY + " from the receive however often its visibility is extended. Time on "
                    + "the queue before the claim and the work after the hold add to that. A claim held past the "
                    + "ceiling is redelivered to another node while this one still has it, and the work is "
                    + "duplicated. Keep " + propertyName + " well under " + SQS_MAX_VISIBILITY + "."));
        }
    }

    /**
     * Every bound is stated (R14) and positive.
     */
    private void validateAggregateBounds(final AggregateStageConfig stage,
                                         final List<PipelineValidationIssue> issues) {
        requireBound(stage.getMaxItemsPerAggregate() == null, stage.getMaxItemsPerAggregate() != null
                && stage.getMaxItemsPerAggregate() < 1, "maxItemsPerAggregate", issues);
        requireBound(stage.getMaxUncompressedByteSize() == null, stage.getMaxUncompressedByteSize() != null
                && stage.getMaxUncompressedByteSize() < 1, "maxUncompressedByteSize", issues);
        final StroomDuration frequency = stage.getAggregationFrequency();
        requireBound(frequency == null, frequency != null
                && (frequency.isZero() || frequency.getDuration().isNegative()), "aggregationFrequency", issues);
    }

    private static void requireBound(final boolean absent,
                                     final boolean invalid,
                                     final String name,
                                     final List<PipelineValidationIssue> issues) {
        if (absent) {
            issues.add(PipelineValidationIssue.errorForStage(
                    PipelineStageName.AGGREGATE,
                    CODE_AGGREGATE_BOUND_NOT_STATED,
                    "Stage aggregate does not state " + name + ". Every bound must be stated."));
        } else if (invalid) {
            issues.add(PipelineValidationIssue.errorForStage(
                    PipelineStageName.AGGREGATE,
                    CODE_AGGREGATE_BOUND_INVALID,
                    "Stage aggregate has a " + name + " that is not positive."));
        }
    }

    private void validateForwardStage(final ProxyPipelineConfig pipelineConfig,
                                      final ForwardStageConfig stage,
                                      final List<PipelineValidationIssue> issues) {
        if (stage == null || !stage.isEnabled()) {
            return;
        }

        validateRequiredInputQueue(pipelineConfig, PipelineStageName.FORWARD,
                stage.getInputQueue(), issues);
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


    /**
     * The mode is stated, and every queue and store fits it. A local queue cannot carry a reference
     * to another node and a distributed queue cannot carry a node-local path, so a mixture is not a
     * third mode; it is refused.
     */
    private void validateMode(final ProxyPipelineConfig pipelineConfig,
                              final List<PipelineValidationIssue> issues) {
        final PipelineMode mode = pipelineConfig.getMode();
        if (mode == null) {
            issues.add(PipelineValidationIssue.error(
                    CODE_PIPELINE_MODE_NOT_STATED,
                    "pipeline.mode must be stated: LOCAL (one node, local queues and file stores) or SHARED "
                    + "(many nodes, distributed queues and shared file stores)"));
            return;
        }

        final Map<String, QueueDefinition> queues = pipelineConfig.getQueues();
        if (queues != null) {
            queues.forEach((queueName, definition) -> {
                if (definition == null) {
                    return;
                }
                final QueueType queueType = Objects.requireNonNullElse(
                        definition.getType(), QueueDefinition.DEFAULT_TYPE);
                if (!mode.accepts(queueType)) {
                    issues.add(PipelineValidationIssue.errorForQueue(
                            queueName,
                            CODE_QUEUE_TYPE_DOES_NOT_FIT_MODE,
                            "Queue '" + queueName + "' is of type " + queueType + ", which does not fit pipeline.mode "
                            + mode + ": a " + mode + " deployment uses "
                            + (mode == PipelineMode.LOCAL ? "LOCAL_FILESYSTEM" : "SQS or KAFKA") + " queues"));
                }
            });
        }

        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        if (fileStores != null) {
            fileStores.forEach((fileStoreName, definition) -> {
                if (definition == null) {
                    return;
                }
                if (!mode.accepts(definition.getType())) {
                    issues.add(PipelineValidationIssue.errorForFileStore(
                            fileStoreName,
                            CODE_FILE_STORE_TYPE_DOES_NOT_FIT_MODE,
                            "File store '" + fileStoreName + "' is of type " + definition.getType()
                            + ", which does not fit pipeline.mode " + mode + ": a " + mode + " deployment uses "
                            + (mode == PipelineMode.LOCAL
                                    ? "LOCAL_FILESYSTEM"
                                    : "S3 or SHARED_FILESYSTEM") + " file stores"));
                }
            });
        }
    }

    /**
     * How many times a computable wait a shared filesystem store's {@code orphanAge} must cover. One
     * covers the wait itself; the second covers the one redelivery shared mode exists to survive - a
     * node dying mid-aggregate and another holding the input for a fresh window, or the final
     * forward attempt at {@code maxRetryAge} racing the hourly sweep during its give-up. Two node
     * deaths in a row on one input are not covered, and that is accepted.
     */
    static final int ORPHAN_AGE_MARGIN = 2;

    /**
     * An aggregate's inputs stay in their stores, unacknowledged, for as long as the aggregate is
     * open; a shared filesystem store's sweep deletes anything older than its orphanAge whether or
     * not something still names it. So the sweep must not be able to reach a group an open aggregate
     * still needs - including one re-held for a whole second window after its first holder died.
     */
    private void validateOrphanAgeExceedsAggregationWindow(final ProxyPipelineConfig pipelineConfig,
                                                           final List<PipelineValidationIssue> issues) {
        final PipelineStagesConfig stages = pipelineConfig.getStages();
        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        if (stages == null || fileStores == null || !stages.getAggregate().isEnabled()) {
            return;
        }
        final StroomDuration frequency = stages.getAggregate().getAggregationFrequency();
        if (frequency == null) {
            return;
        }
        fileStores.forEach((fileStoreName, definition) -> {
            if (definition == null
                || definition.getType() != FileStoreType.SHARED_FILESYSTEM
                || definition.getOrphanAge() == null) {
                return;
            }
            if (isWithinMargin(definition.getOrphanAge().getDuration(), frequency.getDuration())) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_ORPHAN_AGE_WITHIN_AGGREGATION_WINDOW,
                        "File store '" + fileStoreName + "' has orphanAge " + definition.getOrphanAge()
                        + ", which is less than " + ORPHAN_AGE_MARGIN + " x the aggregate stage's "
                        + "aggregationFrequency " + frequency + ". An input waits in its store, unacknowledged, "
                        + "for as long as its aggregate is open - and for a second window if the node holding "
                        + "it dies and another re-claims it - and the sweep would delete it, silently. Raise "
                        + "orphanAge or lower aggregationFrequency."));
            }
        });
    }

    /**
     * The forward stage's destinations fit the pipeline block ({@code designs/stages/forward.md}
     * §4.3, §4.4): there is one; with several, each has its queue and store stated; in shared mode
     * each gives up somewhere shared; and the queue each drains can hold a claim for as long as the
     * destination's back-off and keeps redelivering for as long as its retry age.
     */
    private void validateForwardDestinations(final ProxyPipelineConfig pipelineConfig,
                                             final List<ForwardDestinationFacts> destinations,
                                             final PathCreator pathCreator,
                                             final List<PipelineValidationIssue> issues) {
        final PipelineStagesConfig stages = pipelineConfig.getStages();
        if (destinations == null
            || stages == null
            || !stages.getConfiguredStages().contains(PipelineStageName.FORWARD)
            || !stages.getForward().isEnabled()) {
            return;
        }
        if (destinations.isEmpty()) {
            issues.add(PipelineValidationIssue.errorForStage(
                    PipelineStageName.FORWARD,
                    CODE_FORWARD_NO_DESTINATION,
                    "The forward stage is enabled but no forward destination is enabled, so nothing would "
                    + "drain " + orDefault(stages.getForward().getInputQueue(),
                            ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)
                    + ". Enable a destination under forwardHttpDestinations, forwardFileDestinations or "
                    + "forwardS3Destinations, or disable the forward stage on this node."));
            return;
        }

        final boolean fanOut = destinations.size() > 1;
        final Map<String, QueueDefinition> queues = Objects.requireNonNullElse(pipelineConfig.getQueues(), Map.of());
        final String stageInputQueue = orDefault(
                stages.getForward().getInputQueue(), ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);

        for (final ForwardDestinationFacts destination : destinations) {
            final String drainedQueue;
            if (fanOut) {
                drainedQueue = FanOutStage.queueNameFor(destination.name());
                if (!hasQueue(pipelineConfig, drainedQueue)) {
                    issues.add(PipelineValidationIssue.errorForStageQueue(
                            PipelineStageName.FORWARD,
                            drainedQueue,
                            CODE_FORWARD_DESTINATION_QUEUE_NOT_STATED,
                            "More than one forward destination is enabled, so each needs a queue of its own: "
                            + "state queue '" + drainedQueue + "' in the pipeline block for destination '"
                            + destination.name() + "'."));
                }
                final String storeName = FanOutStage.storeNameFor(destination.name());
                if (!hasFileStore(pipelineConfig, storeName)) {
                    issues.add(PipelineValidationIssue.errorForStageFileStore(
                            PipelineStageName.FORWARD,
                            storeName,
                            CODE_FORWARD_DESTINATION_STORE_NOT_STATED,
                            "More than one forward destination is enabled, so each needs a file store of its "
                            + "own: state file store '" + storeName + "' in the pipeline block for destination '"
                            + destination.name() + "'."));
                }
            } else {
                drainedQueue = stageInputQueue;
            }

            final QueueDefinition definition = queues.get(drainedQueue);
            if (definition != null) {
                validateQueueFitsRetry(destination, drainedQueue, definition, issues);
            }

            if (pipelineConfig.getMode() == PipelineMode.SHARED
                && destination.giveUpDirectory() != null
                && !isSafelyUnderASharedFileStore(pipelineConfig, destination.giveUpDirectory(), pathCreator)) {
                issues.add(PipelineValidationIssue.errorForStage(
                        PipelineStageName.FORWARD,
                        CODE_FORWARD_GIVE_UP_NOT_SHARED,
                        "Forward destination '" + destination.name() + "' gives up to directory '"
                        + destination.giveUpDirectory() + "', which is not at least two levels below a "
                        + "SHARED_FILESYSTEM file store's path. In SHARED mode the node that gives up on a group "
                        + "may never be asked again, so give-up data must be somewhere every node sees: set the "
                        + "destination's failureDestination to a directory such as <store path>/give-up/<name>, "
                        + "or to an S3 bucket. It must not be the store's path or a direct child of it, because "
                        + "the store's sweep treats each direct child as a writer root and would delete "
                        + "given-up groups older than orphanAge from it."));
            }
        }
    }

    private static void validateQueueFitsRetry(final ForwardDestinationFacts destination,
                                               final String queueName,
                                               final QueueDefinition definition,
                                               final List<PipelineValidationIssue> issues) {
        final QueueType type = Objects.requireNonNullElse(definition.getType(), QueueDefinition.DEFAULT_TYPE);
        if (type == QueueType.KAFKA) {
            // A back-off wait holds the claim, and a Kafka consumer that does not poll within
            // max.poll.interval.ms loses its partitions, and with them the claim.
            final String configured = definition.getConsumerConfig().get("max.poll.interval.ms");
            final Duration maxPollInterval;
            try {
                maxPollInterval = Duration.ofMillis(configured != null
                        ? Long.parseLong(configured.trim())
                        : KafkaFileGroupQueue.DEFAULT_MAX_POLL_INTERVAL_MS);
            } catch (final NumberFormatException e) {
                issues.add(PipelineValidationIssue.errorForQueue(
                        queueName,
                        CODE_QUEUE_DEFINITION_INVALID,
                        "Kafka queue '" + queueName + "' has a max.poll.interval.ms of '" + configured
                        + "' in its consumer config, which is not a number of milliseconds."));
                return;
            }
            if (destination.longestRetryDelay().compareTo(maxPollInterval) >= 0) {
                issues.add(PipelineValidationIssue.errorForStageQueue(
                        PipelineStageName.FORWARD,
                        queueName,
                        CODE_FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL,
                        "Forward destination '" + destination.name() + "' waits up to "
                        + destination.longestRetryDelay() + " between attempts (retryDelay, or maxRetryDelay when "
                        + "retryDelayGrowthFactor is above 1), which is not shorter than Kafka queue '" + queueName
                        + "'s max.poll.interval.ms of " + maxPollInterval + ". A back-off wait holds the claim, "
                        + "and a consumer that does not poll within that interval loses it; the delivery that "
                        + "follows the wait adds to the gap, so leave a margin. Lower retryDelay and maxRetryDelay, "
                        + "or raise max.poll.interval.ms in the queue's consumer config."));
            }
        }
        if (type == QueueType.SQS) {
            // The wait is served with the claim held and the heartbeat running, and SQS caps how long
            // the heartbeat can keep it.
            warnIfNearSqsVisibilityCeiling(PipelineStageName.FORWARD, queueName,
                    "the longest back-off wait (retryDelay, or maxRetryDelay when it grows)",
                    destination.longestRetryDelay(), issues);
        }
        if (type == QueueType.KAFKA || type == QueueType.LOCAL_FILESYSTEM) {
            // The queue's attempt bound is the backstop beneath the retry age; if it fires first
            // the group reaches the queue's give-up with its data still in the store.
            final Duration attemptsWindow = destination.retryDelay().multipliedBy(definition.getMaxDeliveryAttempts());
            if (attemptsWindow.compareTo(destination.maxRetryAge()) < 0) {
                issues.add(PipelineValidationIssue.warningForStage(
                        PipelineStageName.FORWARD,
                        CODE_FORWARD_ATTEMPTS_END_BEFORE_AGE,
                        "Queue '" + queueName + "' allows " + definition.getMaxDeliveryAttempts()
                        + " delivery attempts and forward destination '" + destination.name() + "' waits "
                        + destination.retryDelay() + " between them, so a destination that stays down is "
                        + "given up on by the queue after about " + attemptsWindow + ", before the "
                        + "destination's maxRetryAge of " + destination.maxRetryAge() + ". Groups would then "
                        + "reach the queue's failed area rather than the destination's failure destination. "
                        + "Raise maxDeliveryAttempts or retryDelay, or lower maxRetryAge."));
            }
        }
    }

    /**
     * Under a shared store's path, so every node sees it, and at least two levels below it, so the
     * store's sweep cannot mistake it for a writer root: the sweep looks into each direct child of
     * the store's path for numbered groups older than orphanAge, and a give-up directory laid out
     * without a date sub-path is numbered like a writer root.
     */
    private static boolean isSafelyUnderASharedFileStore(final ProxyPipelineConfig pipelineConfig,
                                                         final Path giveUpDirectory,
                                                         final PathCreator pathCreator) {
        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        if (fileStores == null) {
            return false;
        }
        final Path giveUp = giveUpDirectory.toAbsolutePath().normalize();
        return fileStores.values().stream()
                .filter(Objects::nonNull)
                .filter(definition -> definition.getType() == FileStoreType.SHARED_FILESYSTEM)
                .map(FileStoreDefinition::getPath)
                .filter(Objects::nonNull)
                .map(path -> pathCreator != null
                        ? pathCreator.toAppPath(path)
                        : Path.of(path))
                .map(path -> path.toAbsolutePath().normalize())
                .anyMatch(storePath -> giveUp.startsWith(storePath)
                                       && giveUp.getNameCount() >= storePath.getNameCount() + 2);
    }

    private void validateOrphanAgeExceedsRetryWindow(final ProxyPipelineConfig pipelineConfig,
                                                     final Duration longestForwardRetryAge,
                                                     final List<PipelineValidationIssue> issues) {
        final Map<String, FileStoreDefinition> fileStores = pipelineConfig.getFileStores();
        if (longestForwardRetryAge == null || fileStores == null) {
            return;
        }
        fileStores.forEach((fileStoreName, definition) -> {
            if (definition == null
                || definition.getType() != FileStoreType.SHARED_FILESYSTEM
                || definition.getOrphanAge() == null) {
                return;
            }
            if (isWithinMargin(definition.getOrphanAge().getDuration(), longestForwardRetryAge)) {
                issues.add(PipelineValidationIssue.errorForFileStore(
                        fileStoreName,
                        CODE_ORPHAN_AGE_WITHIN_RETRY_WINDOW,
                        "Shared filesystem file store '" + fileStoreName + "' has orphanAge "
                        + definition.getOrphanAge() + ", which is less than " + ORPHAN_AGE_MARGIN
                        + " x the longest forward retry window " + longestForwardRetryAge + ". The sweep would "
                        + "delete a group still being retried, or one whose final attempt is giving up, silently. "
                        + "Raise orphanAge or lower maxRetryAge."));
            }
        });
    }

    /**
     * @return True if {@code orphanAge} is less than {@link #ORPHAN_AGE_MARGIN} times the wait.
     *         Exactly the margin is enough.
     */
    private static boolean isWithinMargin(final Duration orphanAge, final Duration wait) {
        return orphanAge.compareTo(wait.multipliedBy(ORPHAN_AGE_MARGIN)) < 0;
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
        addIfEnabled(consumed, stages.getAggregate().isEnabled(),
                orDefault(stages.getAggregate().getInputQueue(), ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE));
        addIfEnabled(consumed, stages.getForward().isEnabled(),
                orDefault(stages.getForward().getInputQueue(), ProxyPipelineConfig.FORWARDING_INPUT_QUEUE));

        final Map<String, String> published = new LinkedHashMap<>();
        putIfEnabled(published, stages.getReceive().isEnabled(),
                orDefault(stages.getReceive().getOutputQueue(),
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE), PipelineStageName.RECEIVE);
        // The stated split-zip queue is used as stated, so a local one needs a consumer here.
        putIfEnabled(published, stages.getReceive().isEnabled(),
                stages.getReceive().getSplitZipQueue(), PipelineStageName.RECEIVE);
        putIfEnabled(published, stages.getSplitZip().isEnabled(),
                orDefault(stages.getSplitZip().getOutputQueue(),
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE), PipelineStageName.SPLIT_ZIP);
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
