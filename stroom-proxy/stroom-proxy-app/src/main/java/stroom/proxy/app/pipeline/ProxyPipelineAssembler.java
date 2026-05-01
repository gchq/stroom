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

package stroom.proxy.app.pipeline;

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
import stroom.proxy.repo.FeedKey;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bridges the new reference-message pipeline to existing production handlers.
 * <p>
 * This class mirrors the assembly pattern of {@code ReceiverFactoryProvider}
 * but targets the new pluggable queue pipeline. It:
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
     */
    public ProxyPipelineAssembler(final ProxyPipelineConfig pipelineConfig,
                                  final ProxyId proxyId,
                                  final PreAggregator preAggregator,
                                  final Aggregator aggregator,
                                  final Forwarder forwarder,
                                  final SimpleReceiver simpleReceiver,
                                  final ZipReceiver zipReceiver,
                                  final PathCreator pathCreator) {

        Objects.requireNonNull(pipelineConfig, "pipelineConfig");
        Objects.requireNonNull(proxyId, "proxyId");
        Objects.requireNonNull(preAggregator, "preAggregator");
        Objects.requireNonNull(aggregator, "aggregator");
        Objects.requireNonNull(forwarder, "forwarder");
        Objects.requireNonNull(simpleReceiver, "simpleReceiver");
        Objects.requireNonNull(zipReceiver, "zipReceiver");
        Objects.requireNonNull(pathCreator, "pathCreator");

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

        // Build file store registry from the factory (all configured stores).
        final FileStoreRegistry fileStoreRegistry = FileStoreRegistry.fromFactory(fileStoreFactory);

        // -- Pre-aggregate stage processor --
        // Wire the PreAggregator's destination to an AggregateClosePublisher
        // that publishes closed pre-aggregates to the aggregate input queue.
        final FileStore preAggregateStore = fileStoreFactory.getFileStore(
                ProxyPipelineConfig.PRE_AGGREGATE_STORE);
        final FileGroupQueue aggregateInputQueue = queueFactory.getQueue(
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
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
                ProxyPipelineConfig.AGGREGATE_STORE);
        final FileGroupQueue forwardingInputQueue = queueFactory.getQueue(
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
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
                ProxyPipelineConfig.SPLIT_STORE);
        final FileGroupQueue preAggregateInputQueue = queueFactory.getQueue(
                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE);
        stageProcessors.put(
                PipelineStageName.SPLIT_ZIP,
                new SplitZipStageProcessor(
                        fileStoreRegistry,
                        splitStore,
                        preAggregateInputQueue,
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
                        }));

        // 4. Build the runtime with all stage processors.
        this.runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                queueFactory,
                fileStoreFactory,
                stageProcessors);

        // 5. Wire the receive stage — ReceiveStagePublisher as destination on receivers.
        final FileStore receiveStore = fileStoreFactory.getFileStore(
                ProxyPipelineConfig.RECEIVE_STORE);
        // Determine which queue the receive stage should publish to.
        // If split-zip is enabled, publish to splitZipInput; otherwise preAggregateInput.
        final FileGroupQueue receiveOutputQueue = runtime
                .getStage(PipelineStageName.SPLIT_ZIP)
                .flatMap(ProxyPipelineRuntime.RuntimeStage::getInputQueue)
                .orElse(preAggregateInputQueue);

        final ReceiveStagePublisher receiveStagePublisher = new ReceiveStagePublisher(
                receiveStore,
                receiveOutputQueue,
                null, // splitZipQueue — not used for routing yet
                sourceNodeId);

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
}
