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
import stroom.proxy.app.execution.Phase;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.handler.ForwardRetryConfig;
import stroom.proxy.app.handler.NullDestination;
import stroom.proxy.app.handler.RecordingDestination;
import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStage;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.forward.GiveUp;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStage;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.test.common.MockMetrics;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProxyPipelineAssembler} wiring logic.
 * <p>
 * Uses a test harness that replicates the same assembly steps as the
 * production assembler but with lightweight capturing lambdas in place
 * of the heavy production handler classes. This allows us to verify the
 * wiring contract without Guice or filesystem infrastructure.
 * </p>
 */
public class TestProxyPipelineAssembler extends StroomUnitTest {

    @Test
    void testRuntimeIsAssembledWithAllStages() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.runtime.getStages())
                .as("All 4 stages should be present")
                .hasSize(4);
    }

    @Test
    void testRuntimeHasAllQueues() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        final Map<String, FileGroupQueue> queues = harness.runtime.getQueues();

        assertThat(queues)
                .as("All 3 queues should be created")
                .containsKey(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
    }

    @Test
    void testRuntimeHasAllFileStores() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        final Map<String, FileStore> fileStores = harness.runtime.getFileStores();

        assertThat(fileStores)
                .as("All 3 file stores should be created")
                .containsKey(ProxyPipelineConfig.RECEIVE_STORE)
                .containsKey(ProxyPipelineConfig.SPLIT_STORE)
                .containsKey(ProxyPipelineConfig.AGGREGATE_STORE);
    }

    @Test
    void testRegistryHasFourStageLoops() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.registry.loops())
                .as("split-zip, forward, and the aggregate stage's claimer and merge loops")
                .hasSize(4)
                .extracting(loop -> loop.getName())
                .containsExactlyInAnyOrder("stage-splitZip", "stage-forward",
                        AggregateStage.CLAIMER_LOOP, AggregateStage.MERGE_LOOP);
    }

    @Test
    void testReceiveStageHasNoWorker() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.runtime.getWorker(PipelineStageName.RECEIVE))
                .as("Receive stage should not have a queue worker")
                .isEmpty();
    }

    @Test
    void testQueueConsumingStagesHaveWorkers() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.runtime.getWorker(PipelineStageName.SPLIT_ZIP))
                .as("Split-zip stage should have a worker")
                .isPresent();
        assertThat(harness.runtime.getWorker(PipelineStageName.AGGREGATE))
                .as("The aggregate stage keeps its items, so it is not a worker over a processor")
                .isEmpty();
        assertThat(harness.runtime.getWorker(PipelineStageName.FORWARD))
                .as("Forward stage should have a worker")
                .isPresent();
    }

    // -------------------------------------------------------------------------
    // Test Harness — replicates ProxyPipelineAssembler wiring
    // -------------------------------------------------------------------------

    private static class AssemblerTestHarness {

        final ProxyPipelineRuntime runtime;
        final WorkRegistry registry;

        // Captured handler calls
        final List<Path> forwarderAddCalls = new ArrayList<>();

        AssemblerTestHarness(final Path testDir) {
            final String sourceNodeId = "test-proxy-node";

            // Build a fully-wired config with all stages enabled
            final ProxyPipelineConfig config = createFullPipelineConfig();

            final PathCreator pathCreator = new TestPathCreator(testDir);

            // Build factories
            final FileGroupQueueFactory queueFactory = new FileGroupQueueFactory(config, pathCreator);
            final FileStoreFactory fileStoreFactory = new FileStoreFactory(config, pathCreator);
            final FileStoreRegistry fileStoreRegistry = FileStoreRegistry.fromFactory(fileStoreFactory);

            // Build stage processors
            final EnumMap<PipelineStageName, FileGroupQueueItemProcessor> stageProcessors =
                    new EnumMap<>(PipelineStageName.class);

            // Forward: record what is delivered
            stageProcessors.put(PipelineStageName.FORWARD,
                    new ForwardStage(fileStoreRegistry, new RecordingDestination(forwarderAddCalls),
                            new GiveUp(new NullDestination()), new ForwardRetryConfig().toBounds(), () -> false));

            // Split-zip
            final FileStore splitStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.SPLIT_STORE);
            final FileGroupQueue aggregateInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
            stageProcessors.put(PipelineStageName.SPLIT_ZIP,
                    new SplitZipStage(fileStoreRegistry, splitStore, aggregateInputQueue, sourceNodeId));
            // Build the runtime
            this.runtime = ProxyPipelineRuntime.fromConfig(
                    config, queueFactory, fileStoreFactory, stageProcessors);

            // Register the stage loops
            this.registry = new WorkRegistry();
            ProxyPipelineAssembler.registerStages(registry, runtime);
            // The aggregate stage, as the assembler registers it
            final AggregateStage aggregateStage = new AggregateStage(
                    aggregateInputQueue,
                    fileStoreRegistry,
                    fileStoreFactory.getFileStore(ProxyPipelineConfig.AGGREGATE_STORE),
                    queueFactory.getQueue(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE),
                    config.getStages().getAggregate().getBounds(),
                    1,
                    sourceNodeId,
                    new MockMetrics());
            registry.loop(AggregateStage.CLAIMER_LOOP, Phase.AGGREGATE, 1, aggregateStage.claimer());
            registry.loop(AggregateStage.MERGE_LOOP, Phase.AGGREGATE, 1, aggregateStage.merger());
        }
    }

    // -------------------------------------------------------------------------
    // Configuration helper — fully enabled config
    // -------------------------------------------------------------------------

    // Public so the real-assembler test in stroom.proxy.app.handler can use the same config
    // rather than repeat it - a repeated wiring drifts from the real one (test code only).
    public static ProxyPipelineConfig createFullPipelineConfig() {
        return new ProxyPipelineConfig(
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
    }

    private static Map<String, QueueDefinition> defaultQueues() {
        return Map.of(
                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE, new QueueDefinition(),
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE, new QueueDefinition());
    }

    private static Map<String, FileStoreDefinition> defaultFileStores() {
        return Map.of(
                ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("stores/receive"),
                ProxyPipelineConfig.SPLIT_STORE, new FileStoreDefinition("stores/split"),
                ProxyPipelineConfig.AGGREGATE_STORE, new FileStoreDefinition("stores/aggregate"));
    }

    // -------------------------------------------------------------------------
    // TestPathCreator — simple path resolver for tests
    // -------------------------------------------------------------------------

    private static final class TestPathCreator implements PathCreator {

        private final Path root;

        private TestPathCreator(final Path root) {
            this.root = root;
        }

        @Override
        public String replaceTimeVars(final String path) {
            return path;
        }

        @Override
        public String replaceTimeVars(final String path,
                                      final ZonedDateTime dateTime) {
            return path;
        }

        @Override
        public String replaceSystemProperties(final String path) {
            return path;
        }

        @Override
        public Path toAppPath(final String pathString) {
            final Path path = Path.of(pathString);
            if (path.isAbsolute()) {
                return path.normalize();
            }
            return root.resolve(path).normalize();
        }

        @Override
        public String replaceUUIDVars(final String path) {
            return path;
        }

        @Override
        public String replaceFileName(final String path,
                                      final String fileName) {
            return path;
        }

        @Override
        public String[] findVars(final String path) {
            return new String[0];
        }

        @Override
        public boolean containsVars(final String path) {
            return false;
        }

        @Override
        public String replace(final String path,
                              final String var,
                              final LongSupplier replacementSupplier,
                              final int pad) {
            return path;
        }

        @Override
        public String replace(final String str,
                              final String var,
                              final Supplier<String> replacementSupplier) {
            return str;
        }

        @Override
        public String replaceAll(final String path) {
            return path;
        }

        @Override
        public String replaceContextVars(final String path) {
            return path;
        }
    }
}
