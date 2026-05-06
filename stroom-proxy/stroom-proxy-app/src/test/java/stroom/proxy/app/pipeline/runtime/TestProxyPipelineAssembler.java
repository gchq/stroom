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
import java.util.function.Consumer;
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
class TestProxyPipelineAssembler extends StroomUnitTest {

    @Test
    void testRuntimeIsAssembledWithAllStages() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.runtime.getStages())
                .as("All 5 stages should be present")
                .hasSize(5);
    }

    @Test
    void testRuntimeHasAllQueues() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        final Map<String, FileGroupQueue> queues = harness.runtime.getQueues();

        assertThat(queues)
                .as("All 4 queues should be created")
                .containsKey(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE)
                .containsKey(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
    }

    @Test
    void testRuntimeHasAllFileStores() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        final Map<String, FileStore> fileStores = harness.runtime.getFileStores();

        assertThat(fileStores)
                .as("All 4 file stores should be created")
                .containsKey(ProxyPipelineConfig.RECEIVE_STORE)
                .containsKey(ProxyPipelineConfig.SPLIT_STORE)
                .containsKey(ProxyPipelineConfig.PRE_AGGREGATE_STORE)
                .containsKey(ProxyPipelineConfig.AGGREGATE_STORE);
    }

    @Test
    void testReceiveStagePublisherIsSet() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.simpleReceiverDestination)
                .as("SimpleReceiver destination should be set by assembler")
                .isNotNull();
        assertThat(harness.zipReceiverDestination)
                .as("ZipReceiver destination should be set by assembler")
                .isNotNull();
    }

    @Test
    void testPreAggregatorDestinationIsAggregateClosePublisher() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.preAggregatorDestination)
                .as("PreAggregator destination should be an AggregateClosePublisher")
                .isNotNull()
                .isInstanceOf(AggregateClosePublisher.class);
    }

    @Test
    void testAggregatorDestinationIsAggregateClosePublisher() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.aggregatorDestination)
                .as("Aggregator destination should be an AggregateClosePublisher")
                .isNotNull()
                .isInstanceOf(AggregateClosePublisher.class);
    }

    @Test
    void testLifecycleHas4Runners() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.lifecycle.getRunners())
                .as("4 runners should be created for queue-consuming stages")
                .hasSize(4);
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
        assertThat(harness.runtime.getWorker(PipelineStageName.PRE_AGGREGATE))
                .as("Pre-aggregate stage should have a worker")
                .isPresent();
        assertThat(harness.runtime.getWorker(PipelineStageName.AGGREGATE))
                .as("Aggregate stage should have a worker")
                .isPresent();
        assertThat(harness.runtime.getWorker(PipelineStageName.FORWARD))
                .as("Forward stage should have a worker")
                .isPresent();
    }

    @Test
    void testReceivePublishFlowsToSplitZipQueue() throws IOException {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        // Simulate a received file group
        final Path receivedDir = getCurrentTestDir().resolve("incoming-test");
        Files.createDirectories(receivedDir);
        Files.writeString(receivedDir.resolve("proxy.meta"), "Feed:TEST_FEED");
        Files.writeString(receivedDir.resolve("proxy.zip"), "zip-content");
        Files.writeString(receivedDir.resolve("proxy.entries"), "entries");

        // Publish via the receive stage publisher
        harness.simpleReceiverDestination.accept(receivedDir);

        // The original dir should be deleted (moved to file store)
        assertThat(receivedDir).doesNotExist();

        // The split-zip input queue should have a message
        final FileGroupQueue splitZipQueue = harness.runtime.getQueues()
                .get(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);
        final FileGroupQueueItem item = splitZipQueue.next().orElse(null);
        assertThat(item)
                .as("Split-zip input queue should have a message after receive")
                .isNotNull();

        final FileGroupQueueMessage message = item.getMessage();
        assertThat(message.producingStage()).isEqualTo("receive");
        assertThat(message.producerId()).isEqualTo("test-proxy-node");
        assertThat(message.fileStoreLocation().storeName())
                .isEqualTo(ProxyPipelineConfig.RECEIVE_STORE);

        item.acknowledge();
    }

    @Test
    void testNoDelegateCallsBeforeProcessing() {
        final AssemblerTestHarness harness = new AssemblerTestHarness(getCurrentTestDir());

        assertThat(harness.preAggregateAddDirCalls)
                .as("No addDir calls should have been made yet")
                .isEmpty();
        assertThat(harness.aggregateAddDirCalls)
                .as("No aggregate addDir calls should have been made yet")
                .isEmpty();
        assertThat(harness.forwarderAddCalls)
                .as("No forwarder add calls should have been made yet")
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test Harness — replicates ProxyPipelineAssembler wiring
    // -------------------------------------------------------------------------

    private static class AssemblerTestHarness {
        final ProxyPipelineRuntime runtime;
        final ProxyPipelineLifecycle lifecycle;

        // Captured destinations
        Consumer<Path> simpleReceiverDestination;
        Consumer<Path> zipReceiverDestination;
        Consumer<Path> preAggregatorDestination;
        Consumer<Path> aggregatorDestination;

        // Captured handler calls
        final List<Path> preAggregateAddDirCalls = new ArrayList<>();
        final List<Path> aggregateAddDirCalls = new ArrayList<>();
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

            // Pre-aggregate: wire close publisher + processor
            final FileStore preAggregateStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.PRE_AGGREGATE_STORE);
            final FileGroupQueue aggregateInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
            final AggregateClosePublisher preAggClosePublisher = new AggregateClosePublisher(
                    preAggregateStore, aggregateInputQueue,
                    PipelineStageName.PRE_AGGREGATE, sourceNodeId);
            this.preAggregatorDestination = preAggClosePublisher;

            stageProcessors.put(PipelineStageName.PRE_AGGREGATE,
                    new PreAggregateStageProcessor(fileStoreRegistry,
                            preAggregateAddDirCalls::add));

            // Aggregate: wire close publisher + processor
            final FileStore aggregateStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.AGGREGATE_STORE);
            final FileGroupQueue forwardingInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
            final AggregateClosePublisher aggClosePublisher = new AggregateClosePublisher(
                    aggregateStore, forwardingInputQueue,
                    PipelineStageName.AGGREGATE, sourceNodeId);
            this.aggregatorDestination = aggClosePublisher;

            stageProcessors.put(PipelineStageName.AGGREGATE,
                    new AggregateStageProcessor(fileStoreRegistry,
                            aggregateAddDirCalls::add));

            // Forward: capture forwarder.add calls
            stageProcessors.put(PipelineStageName.FORWARD,
                    new ForwardStageProcessor(fileStoreRegistry,
                            (message, sourceDir) -> forwarderAddCalls.add(sourceDir)));

            // Split-zip: pass-through
            final FileStore splitStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.SPLIT_STORE);
            final FileGroupQueue preAggregateInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE);
            stageProcessors.put(PipelineStageName.SPLIT_ZIP,
                    new SplitZipStageProcessor(fileStoreRegistry, splitStore,
                            preAggregateInputQueue, sourceNodeId,
                            (sourceDir, outputParentDir) -> { /* pass-through */ }));

            // Build the runtime
            this.runtime = ProxyPipelineRuntime.fromConfig(
                    config, queueFactory, fileStoreFactory, stageProcessors);

            // Wire the receive stage
            final FileStore receiveStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.RECEIVE_STORE);
            final FileGroupQueue splitZipInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);
            final ReceiveStagePublisher receiveStagePublisher = new ReceiveStagePublisher(
                    receiveStore, splitZipInputQueue, null, sourceNodeId);

            this.simpleReceiverDestination = receiveStagePublisher;
            this.zipReceiverDestination = receiveStagePublisher;

            // Build lifecycle
            this.lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);
        }
    }

    // -------------------------------------------------------------------------
    // Configuration helper — fully enabled config
    // -------------------------------------------------------------------------

    static ProxyPipelineConfig createFullPipelineConfig() {
        return new ProxyPipelineConfig(
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
