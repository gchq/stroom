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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.execution.Loop;
import stroom.proxy.app.execution.Phase;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.pipeline.config.ForwardDestinationFacts;
import stroom.proxy.app.pipeline.config.PipelineMode;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStage;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.FanOutStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardDestinations;
import stroom.proxy.app.pipeline.stage.forward.ForwardStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.forward.LivenessWatch;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStage;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place the pipeline is assembled from its configuration.
 * <ol>
 *     <li>Validates the configuration, once, and halts on any error.</li>
 *     <li>Builds the queues and file stores through {@link FileGroupQueueFactory} and
 *         {@link FileStoreFactory}.</li>
 *     <li>Builds the split-zip and forward stage processors, and the aggregate stage, which has
 *         loops of its own because its claimer keeps the items it takes.</li>
 *     <li>Builds the {@link ProxyPipelineRuntime}, sweeps orphans in local mode, and resolves the
 *         receive stage's store and queues as {@link ReceiveWiring}, from which
 *         {@code ProxyCoreModule} builds the receiver.</li>
 *     <li>Registers every stage loop and the shared-store sweep with the work registry.</li>
 * </ol>
 */
public class ProxyPipelineAssembler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyPipelineAssembler.class);

    private final ReceiveWiring receiveWiring;
    private final ProxyPipelineRuntime runtime;

    /**
     * @param forwardDestinations The enabled forward destinations. With one, the forward stage's
     *                            loop delivers to it; with several, the stage fans out and each
     *                            destination gets a loop of its own.
     */
    public ProxyPipelineAssembler(final ProxyPipelineConfig pipelineConfig,
                                  final ProxyId proxyId,
                                  final List<ForwardDestinations.Wiring> forwardDestinations,
                                  final PathCreator pathCreator,
                                  final Durability durability,
                                  final Metrics metrics,
                                  final WorkRegistry workRegistry) {

        Objects.requireNonNull(durability, "durability");
        Objects.requireNonNull(workRegistry, "workRegistry");
        Objects.requireNonNull(pipelineConfig, "pipelineConfig");
        Objects.requireNonNull(proxyId, "proxyId");
        Objects.requireNonNull(forwardDestinations, "forwardDestinations");
        Objects.requireNonNull(pathCreator, "pathCreator");
        Objects.requireNonNull(metrics, "metrics");

        final String sourceNodeId = proxyId.getId();

        LOGGER.info(() -> LogUtil.message(
                "Assembling reference-message pipeline (sourceNodeId: {})", sourceNodeId));

        // R1's durability invariant is the same in every mode; what changes is who guarantees
        // it. Below FULL the proxy is relying on the filesystem writing data before the metadata that
        // publishes it - true of ext4's default data=ordered and of XFS, and NOT something this
        // process can verify, so it is stated loudly rather than assumed quietly. An operator who did
        // not intend it sees it at every start; one who did can read it as confirmation.
        if (durability != Durability.FULL) {
            LOGGER.warn(() -> LogUtil.message(
                    "durability is set to {}, not FULL. {} This is only correct on a filesystem that "
                    + "writes data before the metadata naming it - ext4 with data=ordered, XFS - or "
                    + "on storage that provides the ordering itself. The proxy cannot check that, so "
                    + "if you are unsure, set durability to FULL.",
                    durability,
                    durability == Durability.QUEUE_ONLY
                            ? "Queue messages are still forced, so committed data stays findable."
                            : "Queue messages are not forced. File stores have their own durability "
                              + "setting and are unaffected."));
        }

        // 1. Build factories from config. The longest forward retry window goes to the queue factory
        //    because a broker's own retention has to exceed it, and only the broker can say what it is
        //    (SqsFileGroupQueue.requireQueueFitsPipeline).
        final Duration longestForwardRetryAge = forwardDestinations.stream()
                .map(wiring -> wiring.bounds().maxRetryAge())
                .max(Comparator.naturalOrder())
                .orElse(null);
        final FileGroupQueueFactory queueFactory = new FileGroupQueueFactory(
                pipelineConfig.getQueues(), pathCreator, durability, longestForwardRetryAge);
        final FileStoreFactory fileStoreFactory =
                new FileStoreFactory(pipelineConfig.getFileStores(), pathCreator);

        // 2. Build stage processors wired to production handlers.
        final Map<PipelineStageName, FileGroupQueueItemProcessor> stageProcessors =
                new EnumMap<>(PipelineStageName.class);

        // 3. Processors are built from the factories, which cache by name, so the runtime built
        //    below shares every queue and store instance with them.

        // Every name this assembler wires is resolved the same way - the stage's own configuration,
        // falling back to the pipeline default when the operator has not named one - on the producing
        // and the consuming side alike, so a renamed queue or store is one object to both.
        final PipelineStagesConfig stagesConfig = pipelineConfig.getStages();

        // The one validation of the pipeline configuration: every error it can find, once, before
        // anything is built, and the boot halts on any. The warnings are how an operator sees which
        // stages this process is actually running, so they are logged rather than discarded.
        final List<ForwardDestinationFacts> destinationFacts = forwardDestinations.stream()
                .map(ForwardDestinations.Wiring::toFacts)
                .toList();
        final PipelineValidationResult validationResult =
                new ProxyPipelineConfigValidator().validate(pipelineConfig, destinationFacts, pathCreator);
        validationResult.getWarnings().forEach(warning ->
                LOGGER.warn(() -> LogUtil.message("Pipeline configuration: {}", warning)));
        validationResult.throwIfInvalid();


        // Everything below builds queues and file stores. A later step that throws must not leave a
        // partially assembled pipeline holding its locks and handles: on a local queue the lock is
        // what stops a second process touching the directory.
        try {
            // Build file store registry from the factory (all configured stores).
            final FileStoreRegistry fileStoreRegistry = FileStoreRegistry.fromFactory(fileStoreFactory);

            // -- Forward stage --
            //
            // With one destination the stage's loop is that destination's loop: it claims a group,
            // delivers it and returns, or throws so the message goes to the tail of the queue with
            // the attempt counted (forward.md F1, F3). With several the stage fans out, copying each
            // group into every destination's own store and queue, and each destination drains its
            // own queue with a loop of its own (F7).
            final ForwardStageConfig forwardConfig = stagesConfig.getForward();
            final Map<PipelineStageName, Integer> threadOverrides = new EnumMap<>(PipelineStageName.class);
            final List<String> fanOutQueues = new ArrayList<>();
            final List<String> fanOutStores = new ArrayList<>();
            if (forwardConfig.isEnabled()) {
                if (forwardDestinations.size() == 1) {
                    final ForwardDestinations.Wiring only = forwardDestinations.getFirst();
                    stageProcessors.put(PipelineStageName.FORWARD, forwardStage(fileStoreRegistry, only, workRegistry));
                    threadOverrides.put(PipelineStageName.FORWARD, only.consumerThreads());
                } else {
                    final List<FanOutStage.Target> targets = new ArrayList<>();
                    for (final ForwardDestinations.Wiring wiring : forwardDestinations) {
                        final String queueName = FanOutStage.queueNameFor(wiring.name());
                        final String storeName = FanOutStage.storeNameFor(wiring.name());
                        targets.add(new FanOutStage.Target(
                                wiring.name(),
                                fileStoreFactory.getFileStore(storeName),
                                queueFactory.getQueue(queueName)));
                        fanOutQueues.add(queueName);
                        fanOutStores.add(storeName);
                    }
                    stageProcessors.put(PipelineStageName.FORWARD,
                            new FanOutStage(fileStoreRegistry, targets, sourceNodeId));
                }
            }

            // -- Split-zip stage --
            final FileStore splitStore = fileStoreFactory.getFileStore(
                    orDefault(stagesConfig.getSplitZip().getFileStore(),
                            ProxyPipelineConfig.SPLIT_STORE));
            final FileGroupQueue splitZipOutputQueue = queueFactory.getQueue(
                    orDefault(stagesConfig.getSplitZip().getOutputQueue(),
                            ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE));
            stageProcessors.put(
                    PipelineStageName.SPLIT_ZIP,
                    new SplitZipStage(fileStoreRegistry, splitStore, splitZipOutputQueue, sourceNodeId));

            // 4. Build the runtime with all stage processors.
            this.runtime = ProxyPipelineRuntime.fromConfig(
                    pipelineConfig,
                    queueFactory,
                    fileStoreFactory,
                    stageProcessors,
                    fanOutQueues,
                    fanOutStores,
                    threadOverrides);

            // In local mode this process is the only writer, and nothing is running yet, so this is
            // the one moment the stores can be compared with the queues exactly: every committed
            // group that no message names is an orphan, and goes now rather than on a timer.
            if (pipelineConfig.getMode() == PipelineMode.LOCAL) {
                LocalModeStartUp.sweepOrphans(pipelineConfig, queueFactory, fileStoreFactory);
            }

            // 5. Resolve what receipt writes to and publishes on. A disabled receive stage has no
            //    wiring, and gets a receiver that refuses every call.
            if (stagesConfig.getReceive().isEnabled()) {
                final FileStore receiveStore = fileStoreFactory.getFileStore(
                        orDefault(stagesConfig.getReceive().getFileStore(),
                                ProxyPipelineConfig.RECEIVE_STORE));
                final FileGroupQueue receiveOutputQueue = queueFactory.getQueue(
                        orDefault(stagesConfig.getReceive().getOutputQueue(),
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE));
                // Where a zip holding more than one feed goes. A configured name always wins, and is
                // independent of whether the split-zip stage runs in this process: in a split
                // deployment receive runs here and split-zip elsewhere, draining the same queue. Only
                // the default follows whether a split-zip stage exists here, because with neither a
                // stage nor a configured name nothing drains the queue, and receipt refuses such zips
                // rather than strand them.
                final String splitZipQueueName = resolveReceiveSplitZipQueue(stagesConfig);
                final FileGroupQueue splitZipQueue = splitZipQueueName == null
                        ? null
                        : queueFactory.getQueue(splitZipQueueName);
                this.receiveWiring = new ReceiveWiring(receiveStore, receiveOutputQueue, splitZipQueue);
            } else {
                this.receiveWiring = null;
            }

            // 6. Register the stages' consumer loops and the shared-store sweep with the registry,
            //    each in its phase, so start and stop follow the flow of data.
            final Map<PipelineStageName, Loop> stageLoops = registerStages(workRegistry, runtime);

            // Each destination's loop and, where it has a liveness check, the watch that pauses the
            // loop while the destination is down (forward.md F6). With one destination its loop is the
            // stage's; with several each drains its own fan-out queue.
            if (forwardConfig.isEnabled()) {
                if (forwardDestinations.size() == 1) {
                    watchLiveness(workRegistry, forwardDestinations.getFirst(),
                            stageLoops.get(PipelineStageName.FORWARD));
                } else {
                    for (final ForwardDestinations.Wiring wiring : forwardDestinations) {
                        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                                queueFactory.getQueue(FanOutStage.queueNameFor(wiring.name())),
                                forwardStage(fileStoreRegistry, wiring, workRegistry));
                        final Loop loop = workRegistry.loop(
                                FanOutStage.queueNameFor(wiring.name()),
                                Phase.FORWARD,
                                wiring.consumerThreads(),
                                () -> worker.processNext().loopOutcome());
                        watchLiveness(workRegistry, wiring, loop);
                    }
                }
            }

            // The aggregate stage is not a worker over a processor: its claimer keeps the items it
            // takes, so it has loops of its own. The claimer is registered first so that it stops
            // first, then the merge workers.
            final AggregateStageConfig aggregateConfig = stagesConfig.getAggregate();
            if (aggregateConfig.isEnabled()) {
                final AggregateStage aggregateStage = new AggregateStage(
                        queueFactory.getQueue(orDefault(aggregateConfig.getInputQueue(),
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE)),
                        fileStoreRegistry,
                        fileStoreFactory.getFileStore(orDefault(aggregateConfig.getFileStore(),
                                ProxyPipelineConfig.AGGREGATE_STORE)),
                        queueFactory.getQueue(orDefault(aggregateConfig.getOutputQueue(),
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)),
                        aggregateConfig.getBounds(),
                        aggregateConfig.getThreads().getMergeThreads(),
                        sourceNodeId,
                        metrics);
                workRegistry.loop(AggregateStage.CLAIMER_LOOP, Phase.AGGREGATE,
                        aggregateConfig.getThreads().getConsumerThreads(), aggregateStage.claimer());
                workRegistry.loop(AggregateStage.MERGE_LOOP, Phase.AGGREGATE,
                        aggregateConfig.getThreads().getMergeThreads(), aggregateStage.merger());
            }
            final SharedFileStoreSweeper sweeper = SharedFileStoreSweeper.forStores(runtime.getFileStores().values());
            if (sweeper.hasStores()) {
                workRegistry.schedule("file-store-sweep", Phase.HOUSEKEEPING, SharedFileStoreSweeper.INTERVAL,
                        sweeper::sweepAll);
            }

        } catch (final RuntimeException e) {
            queueFactory.closeBuilt();
            fileStoreFactory.closeBuilt();
            throw e;
        }

        LOGGER.info(() -> LogUtil.message(
                "Reference-message pipeline assembled: {} stage(s), {} queue(s), {} file store(s)",
                runtime.getStages().size(),
                runtime.getQueues().size(),
                runtime.getFileStores().size()));
    }

    /**
     * @return What the receiver writes to and publishes on, or null when the receive stage is
     * disabled in this process.
     */
    public ReceiveWiring getReceiveWiring() {
        return receiveWiring;
    }


    /**
     * @return The assembled pipeline runtime (topology, queues, stores, workers).
     */
    public ProxyPipelineRuntime getRuntime() {
        return runtime;
    }

    /**
     * Register one loop per queue-consuming stage, named {@code stage-<configName>}, with the stage's
     * thread count as the runtime holds it, in the stage's {@link Phase}.
     *
     * @return The loops, by stage.
     */
    public static Map<PipelineStageName, Loop> registerStages(final WorkRegistry workRegistry,
                                                              final ProxyPipelineRuntime runtime) {
        final Map<PipelineStageName, Loop> loops = new EnumMap<>(PipelineStageName.class);
        runtime.streamStages()
                .filter(ProxyPipelineRuntime.RuntimeStage::hasWorker)
                .forEach(stage -> {
                    final FileGroupQueueWorker worker = stage.getWorker().orElseThrow();
                    loops.put(stage.stageName(), workRegistry.loop(
                            "stage-" + stage.getConfigName(),
                            phaseOf(stage.stageName()),
                            stage.getThreads().getConsumerThreads(),
                            () -> worker.processNext().loopOutcome()));
                });
        return loops;
    }

    private static ForwardStage forwardStage(final FileStoreRegistry fileStoreRegistry,
                                             final ForwardDestinations.Wiring wiring,
                                             final WorkRegistry workRegistry) {
        return new ForwardStage(
                fileStoreRegistry,
                wiring.destination(),
                wiring.giveUp(),
                wiring.bounds(),
                workRegistry::isShuttingDown);
    }

    private static void watchLiveness(final WorkRegistry workRegistry,
                                      final ForwardDestinations.Wiring wiring,
                                      final Loop loop) {
        wiring.destination().livenessCheck().ifPresent(check ->
                workRegistry.schedule(
                        "liveness-" + wiring.name(),
                        Phase.FORWARD,
                        wiring.livenessCheckInterval(),
                        new LivenessWatch(wiring.name(), check, loop)));
    }

    /**
     * The phase a queue-consuming stage's loop runs in; the stages are phases in pipeline order.
     */
    public static Phase phaseOf(final PipelineStageName stageName) {
        return switch (stageName) {
            case SPLIT_ZIP -> Phase.SPLIT_ZIP;
            case AGGREGATE -> Phase.AGGREGATE;
            case FORWARD -> Phase.FORWARD;
            case RECEIVE -> throw new IllegalArgumentException("The receive stage has no consumer loop");
        };
    }

    /**
     * Where receive sends multi-feed groups: the queue the operator named, or nowhere, in which case
     * receipt refuses a zip holding more than one feed. The pipeline block is what was written and
     * nothing else (R14): a stated queue is used whether or not a split-zip stage runs in this
     * process, since in a split deployment another node drains it, and the validator refuses a
     * stated local queue that no enabled stage here consumes.
     *
     * @return the queue to send multi-feed groups to, or null for no splitting.
     */
    private static String resolveReceiveSplitZipQueue(final PipelineStagesConfig stagesConfig) {
        return stagesConfig.getReceive().getSplitZipQueue();
    }

    /**
     * @return {@code configured} when the operator named a queue or file store for this stage, otherwise
     * the pipeline default. Stage config normalises a blank name to null.
     */
    private static String orDefault(final String configured, final String defaultName) {
        return configured != null
                ? configured
                : defaultName;
    }


    // --------------------------------------------------------------------------------


    /**
     * The receive stage's store and queues, resolved from configuration.
     *
     * @param splitZipQueue Where a zip holding more than one feed goes, or null if nothing here or
     *                      elsewhere would drain such a queue.
     */
    public record ReceiveWiring(FileStore store,
                                FileGroupQueue outputQueue,
                                FileGroupQueue splitZipQueue) {

    }
}
