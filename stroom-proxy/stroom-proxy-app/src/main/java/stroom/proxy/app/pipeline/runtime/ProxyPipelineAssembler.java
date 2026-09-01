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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.Aggregator;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.Forwarder;
import stroom.proxy.app.handler.PreAggregator;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.handler.SimpleReceiver;
import stroom.proxy.app.handler.StoringReceiverFactory;
import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.handler.ZipReceiver;
import stroom.proxy.app.handler.ZipSplitter;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateClosePublisher;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageProcessor;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageProcessor;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageProcessor;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStagePublisher;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageProcessor;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.repo.FeedKey;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bridges the reference-message pipeline to the existing production handlers.
 * <p>
 * This is the single place the runtime topology is assembled, replacing the
 * imperative wiring that used to live in the receive-side receiver factory
 * provider. It:
 * </p>
 * <ol>
 *     <li>Builds {@link FileGroupQueueFactory} and {@link FileStoreFactory}
 *         from the supplied {@link ProxyPipelineConfig}.</li>
 *     <li>Builds stage processors by wiring functional interfaces to
 *         production handler method references:
 *         <ul>
 *             <li>{@code PreAggregateFunction} → {@code PreAggregator::addDir}</li>
 *             <li>{@code AggregateFunction} → {@code Aggregator::addDir}</li>
 *             <li>{@code FileGroupForwarder} → {@code (msg, dir) → forwarder.add(dir)}</li>
 *         </ul>
 *     </li>
 *     <li>Wires aggregate/pre-aggregate destination callbacks as
 *         {@link AggregateClosePublisher} instances that write to a
 *         {@link FileStore} and publish to the output queue.</li>
 *     <li>Builds the {@link ProxyPipelineRuntime} with all stage processors.</li>
 *     <li>Creates a {@link ReceiveStagePublisher} as the receive destination
 *         on {@link SimpleReceiver} and {@link ZipReceiver}.</li>
 *     <li>Produces a {@link ReceiverFactory} (for HTTP ingest) and a
 *         {@link ProxyPipelineLifecycle} (for queue workers).</li>
 * </ol>
 * <p>
 * The servlet layer remains unchanged — {@code ProxyRequestHandler} still calls
 * {@code ReceiverFactory.get()}.
 * </p>
 */
public class ProxyPipelineAssembler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyPipelineAssembler.class);

    /**
     * Split-zip staging, relative to the proxy's configured temp directory.
     * <p>
     * Temp is the right home for it: the data is transient, deleted as soon as the
     * split completes, and the stage clears the directory at startup. Resolving it
     * through {@link TempDirProvider} rather than {@code java.io.tmpdir} means it
     * honours {@code path.temp}, so an operator can point it at memory-backed
     * storage for speed or at disk when file groups are large.
     * </p>
     */
    static final String SPLIT_ZIP_TEMP_SUBDIR = "pipeline/splitZip";

    private final ReceiverFactory receiverFactory;
    private final ProxyPipelineLifecycle lifecycle;
    private final ProxyPipelineRuntime runtime;

    /**
     * Assemble the pipeline by wiring production handlers to stage processors.
     *
     * @param pipelineConfig The pipeline configuration (must be enabled).
     * @param proxyId        The proxy node identifier for message provenance.
     * @param preAggregator  The production pre-aggregator.
     * @param aggregator     The production aggregator.
     * @param forwarder      The production forwarder.
     * @param simpleReceiver The production simple (non-zip) receiver.
     * @param zipReceiver    The production zip receiver.
     * @param pathCreator    Path resolver for queue and file-store paths.
     * @param tempDirProvider Resolves the proxy's configured temp directory, used
     *                        for transient split-zip staging.
     */
    public ProxyPipelineAssembler(final ProxyPipelineConfig pipelineConfig,
                                  final ProxyId proxyId,
                                  final PreAggregator preAggregator,
                                  final Aggregator aggregator,
                                  final Forwarder forwarder,
                                  final SimpleReceiver simpleReceiver,
                                  final ZipReceiver zipReceiver,
                                  final PathCreator pathCreator,
                                  final TempDirProvider tempDirProvider) {

        Objects.requireNonNull(pipelineConfig, "pipelineConfig");
        Objects.requireNonNull(proxyId, "proxyId");
        Objects.requireNonNull(preAggregator, "preAggregator");
        Objects.requireNonNull(aggregator, "aggregator");
        Objects.requireNonNull(forwarder, "forwarder");
        Objects.requireNonNull(simpleReceiver, "simpleReceiver");
        Objects.requireNonNull(zipReceiver, "zipReceiver");
        Objects.requireNonNull(pathCreator, "pathCreator");
        Objects.requireNonNull(tempDirProvider, "tempDirProvider");

        final String sourceNodeId = proxyId.getId();

        LOGGER.info(() -> LogUtil.message(
                "Assembling reference-message pipeline (sourceNodeId: {})", sourceNodeId));

        // 1. Build factories from config.
        final FileGroupQueueFactory queueFactory = new FileGroupQueueFactory(pipelineConfig, pathCreator);
        final FileStoreFactory fileStoreFactory = new FileStoreFactory(pipelineConfig, pathCreator);

        // 2. Build stage processors wired to production handlers.
        final Map<PipelineStageName, FileGroupQueueItemProcessor> stageProcessors =
                new EnumMap<>(PipelineStageName.class);

        // 3. Build the runtime (validates config + creates queues/stores/topology).
        //    We pass an empty processors map first so the runtime creates all queues
        //    and stores, then we build the processors using the runtime's resolved
        //    queues/stores, and finally rebuild with the processors.
        //    Actually, the runtime's fromConfig handles this in a single pass:
        //    it creates queues, stores, and wires workers for stages that have
        //    a processor in the map. So we need to build processors first using
        //    the factories directly.

        // Resolve every name this assembler wires from the stage's own configuration, falling back to
        // the pipeline default when the operator has not named one. Previously the producing side used
        // the ProxyPipelineConfig constants directly while the consuming side used the configured name,
        // so a renamed queue or store left the two halves addressing different objects.
        final PipelineStagesConfig stagesConfig = pipelineConfig.getStages();

        // Deployment-shape check: refuse to start a process that would publish into a local queue no
        // enabled stage drains, and refuse one that has not said which stages it runs. Structural
        // validation happens inside ProxyPipelineRuntime.fromConfig.
        final PipelineValidationResult validationResult =
                new ProxyPipelineConfigValidator().validateDeployment(pipelineConfig);
        // H56: these were computed and discarded, though operations.md, the deployment examples and
        // the validator's own javadoc all say they appear at startup. They are how an operator sees
        // which stages this process is actually running, so they matter most on the change that made
        // an unstated stage set an error.
        validationResult.getWarnings().forEach(warning ->
                LOGGER.warn(() -> LogUtil.message("Pipeline configuration: {}", warning)));
        validationResult.throwIfInvalid();

        // Build file store registry from the factory (all configured stores).
        final FileStoreRegistry fileStoreRegistry = FileStoreRegistry.fromFactory(fileStoreFactory);

        // -- Pre-aggregate stage processor --
        // Wire the PreAggregator's destination to an AggregateClosePublisher
        // that publishes closed pre-aggregates to the aggregate input queue.
        final FileStore preAggregateStore = fileStoreFactory.getFileStore(
                orDefault(stagesConfig.getPreAggregate().getFileStore(),
                        ProxyPipelineConfig.PRE_AGGREGATE_STORE));
        final FileGroupQueue aggregateInputQueue = queueFactory.getQueue(
                orDefault(stagesConfig.getPreAggregate().getOutputQueue(),
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE));
        final AggregateClosePublisher preAggregateClosePublisher = new AggregateClosePublisher(
                preAggregateStore,
                aggregateInputQueue,
                PipelineStageName.PRE_AGGREGATE,
                sourceNodeId);
        preAggregator.setDestination(preAggregateClosePublisher);

        stageProcessors.put(
                PipelineStageName.PRE_AGGREGATE,
                new PreAggregateStageProcessor(fileStoreRegistry, preAggregator::addDir));

        // -- Aggregate stage processor --
        // Wire the Aggregator's destination to an AggregateClosePublisher
        // that publishes merged aggregates to the forwarding input queue.
        final FileStore aggregateStore = fileStoreFactory.getFileStore(
                orDefault(stagesConfig.getAggregate().getFileStore(),
                        ProxyPipelineConfig.AGGREGATE_STORE));
        final FileGroupQueue forwardingInputQueue = queueFactory.getQueue(
                orDefault(stagesConfig.getAggregate().getOutputQueue(),
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE));
        final AggregateClosePublisher aggregateClosePublisher = new AggregateClosePublisher(
                aggregateStore,
                forwardingInputQueue,
                PipelineStageName.AGGREGATE,
                sourceNodeId);
        aggregator.setDestination(aggregateClosePublisher);

        stageProcessors.put(
                PipelineStageName.AGGREGATE,
                new AggregateStageProcessor(fileStoreRegistry, aggregator::addDir));

        // -- Forward stage processor --
        // Wire ForwardStageProcessor to delegate to Forwarder::add (ignoring message).
        stageProcessors.put(
                PipelineStageName.FORWARD,
                new ForwardStageProcessor(
                        fileStoreRegistry,
                        (message, sourceDir) -> forwarder.add(sourceDir)));

        // -- Split-zip stage processor --
        // Wire the existing ZipSplitter.splitZip() static method as the split
        // function. This reads the meta and entries files from the source dir,
        // parses them into feed-keyed entry groups, and delegates to the
        // well-tested ZipSplitter.splitZip() method.
        final FileStore splitStore = fileStoreFactory.getFileStore(
                orDefault(stagesConfig.getSplitZip().getFileStore(),
                        ProxyPipelineConfig.SPLIT_STORE));
        final FileGroupQueue splitZipOutputQueue = queueFactory.getQueue(
                orDefault(stagesConfig.getSplitZip().getOutputQueue(),
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE));
        stageProcessors.put(
                PipelineStageName.SPLIT_ZIP,
                new SplitZipStageProcessor(
                        fileStoreRegistry,
                        splitStore,
                        splitZipOutputQueue,
                        sourceNodeId,
                        (sourceDir, outputParentDir) -> {
                            final FileGroup fileGroup = new FileGroup(sourceDir);
                            final AttributeMap attributeMap = new AttributeMap();
                            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
                            final Map<FeedKey, List<ZipEntryGroup>> allowedEntries =
                                    ZipEntryGroup.read(fileGroup.getEntries())
                                            .stream()
                                            .collect(Collectors.groupingBy(
                                                    ZipEntryGroup::getFeedKey));
                            ZipSplitter.splitZip(
                                    fileGroup.getZip(),
                                    attributeMap,
                                    allowedEntries,
                                    outputParentDir);
                        },
                        tempDirProvider.get().resolve(SPLIT_ZIP_TEMP_SUBDIR)));

        // 4. Build the runtime with all stage processors.
        this.runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                queueFactory,
                fileStoreFactory,
                stageProcessors);

        // 5. Wire the receive stage — ReceiveStagePublisher as destination on receivers.
        final FileStore receiveStore = fileStoreFactory.getFileStore(
                orDefault(stagesConfig.getReceive().getFileStore(),
                        ProxyPipelineConfig.RECEIVE_STORE));
        final FileGroupQueue receiveOutputQueue = queueFactory.getQueue(
                orDefault(stagesConfig.getReceive().getOutputQueue(),
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE));
        // Determine the split-zip queue for multi-feed routing.
        // If the split-zip stage is enabled, multi-feed file groups are routed to
        // the splitZipInput queue; single-feed file groups go directly to preAggregateInput.
        // The receive stage's own splitZipQueue setting decides where multi-feed groups go. It is
        // deliberately independent of whether the split-zip stage runs in this process: in a split
        // deployment receive runs here and split-zip runs elsewhere, consuming the same queue.
        // Defaulted like every other stage name. Leaving it undefaulted meant an omitted
        // stages.receive.splitZipQueue silently disabled multi-feed splitting - and the validation rule
        // that would have caught that was deleted in the same batch that made this field load-bearing.
        // A configured name always wins - that is H2, and it is what makes the receive-here /
        // split-there deployment work. Only the DEFAULT follows whether a split-zip stage exists: with
        // no stage and no configured queue there is nothing to drain a split-zip queue, so publishing
        // to one would strand every multi-feed group.
        final String splitZipQueueName = resolveReceiveSplitZipQueue(stagesConfig);
        final FileGroupQueue splitZipQueue = splitZipQueueName == null
                ? null
                : queueFactory.getQueue(splitZipQueueName);

        final int maxConcurrentReceives = pipelineConfig
                .getStages()
                .getReceive()
                .getThreads()
                .getMaxConcurrentReceives();

        final ReceiveStagePublisher receiveStagePublisher = new ReceiveStagePublisher(
                receiveStore,
                receiveOutputQueue,
                splitZipQueue,
                sourceNodeId,
                maxConcurrentReceives);


        // Set the pipeline publisher as the destination on both receivers.
        simpleReceiver.setDestination(receiveStagePublisher);
        zipReceiver.setDestination(receiveStagePublisher);

        this.receiverFactory = new StoringReceiverFactory(simpleReceiver, zipReceiver);

        // 6. Build the lifecycle.
        this.lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);

        LOGGER.info(() -> LogUtil.message(
                "Reference-message pipeline assembled: {} stage(s), {} queue(s), {} file store(s)",
                runtime.getStages().size(),
                runtime.getQueues().size(),
                runtime.getFileStores().size()));
    }

    /**
     * @return The receiver factory for HTTP ingest, backed by the new pipeline.
     */
    public ReceiverFactory getReceiverFactory() {
        return receiverFactory;
    }

    /**
     * @return The lifecycle manager for queue-consuming stage runners.
     */
    public ProxyPipelineLifecycle getLifecycle() {
        return lifecycle;
    }

    /**
     * @return The assembled pipeline runtime (topology, queues, stores, workers).
     */
    public ProxyPipelineRuntime getRuntime() {
        return runtime;
    }

    /**
     * Decide where receive sends multi-feed groups.
     * <p>
     * This cannot be decided by whether the operator supplied the name, because it is never absent:
     * {@code ProxyConfigurationSourceProvider} deep-merges the compile-time defaults into the YAML
     * before it is parsed, so {@code stages.receive.splitZipQueue} always arrives populated (audit
     * H55). Intent is therefore read from the combination:
     * </p>
     * <ul>
     *   <li>a name that is NOT the pipeline default - the operator chose it, so split to it, whether
     *       or not a split-zip stage runs in this process. That is the receive-here / split-there
     *       deployment, and it is what H2 requires.</li>
     *   <li>the default name with a split-zip stage enabled - split locally.</li>
     *   <li>the default name with no split-zip stage - nothing would drain that queue, so do not
     *       split. Without this, disabling split-zip stranded every multi-feed group.</li>
     * </ul>
     *
     * @return the queue to send multi-feed groups to, or null for no splitting.
     */
    private static String resolveReceiveSplitZipQueue(final PipelineStagesConfig stagesConfig) {
        final String configured = stagesConfig.getReceive().getSplitZipQueue();
        if (configured == null) {
            return null;
        }
        if (!ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE.equals(configured)) {
            return configured;
        }
        return stagesConfig.getSplitZip().isEnabled()
                ? configured
                : null;
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

}
