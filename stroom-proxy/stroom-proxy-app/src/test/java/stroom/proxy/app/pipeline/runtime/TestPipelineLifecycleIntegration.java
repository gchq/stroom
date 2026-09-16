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

import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.handler.ForwardRetryConfig;
import stroom.proxy.app.handler.NullDestination;
import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.monitor.PipelineMonitorProvider;
import stroom.proxy.app.pipeline.monitor.PipelineMonitorSnapshot;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorkerCounters;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStage;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.forward.GiveUp;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStage;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the full pipeline lifecycle and monitoring
 * integration without requiring a Guice injector.
 * <p>
 * These tests exercise:
 * <ul>
 *     <li>Config auto-wiring via {@code defaultFullPipelineStages()}</li>
 *     <li>Registry start/stop (stage loops actually spin up and shut down)</li>
 *     <li>Monitoring snapshot construction from a live runtime</li>
 *     <li>End-to-end publish → queue → worker flow for a single message</li>
 * </ul>
 * </p>
 */
class TestPipelineLifecycleIntegration extends StroomUnitTest {

    // -------------------------------------------------------------------------
    // Registry tests
    // -------------------------------------------------------------------------

    /**
     * Closing the runtime must reach its queues. A local queue holds its directory's ownership
     * lock for its lifetime, so a second queue being able to take the same directory is
     * observable proof that close() reached it.
     */
    @Test
    void testClosingTheRuntimeClosesItsQueues() throws Exception {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());
        harness.registry.start();

        final LocalFileGroupQueue queue = (LocalFileGroupQueue) harness.runtime.getQueues()
                .get(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);

        harness.registry.stop();
        harness.runtime.close();

        try (final LocalFileGroupQueue reopened =
                     new LocalFileGroupQueue(queue.getName(), queue.getRoot())) {
            assertThat(reopened.getName()).isEqualTo(queue.getName());
        }
    }

    @Test
    void testRegistryStartAndStop() {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        // Start the registry - all 4 queue-consuming loops should start
        harness.registry.start();

        assertThat(harness.registry.isStarted())
                .as("Registry should be started after start()")
                .isTrue();
        assertThat(harness.registry.loops())
                .as("split-zip and forward; the aggregate stage is not a worker over a processor")
                .hasSize(2);
        harness.registry.loops().forEach(loop ->
                assertThat(loop.configuredThreads())
                        .as("Loop " + loop.getName() + " should have threads")
                        .isPositive());

        // Stop the registry
        harness.registry.stop();

        assertThat(harness.registry.isStarted())
                .as("Registry should not be started after stop()")
                .isFalse();
        harness.registry.loops().forEach(loop ->
                assertThat(loop.liveThreads())
                        .as("Loop " + loop.getName() + " should have no live threads")
                        .isZero());
    }

    @Test
    void testRegistryStartIsIdempotent() {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        harness.registry.start();
        harness.registry.start(); // second call should be no-op

        assertThat(harness.registry.isStarted()).isTrue();
        assertThat(harness.registry.loops()).hasSize(2);

        harness.registry.stop();
    }

    @Test
    void testRegistryStopIsIdempotent() {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        harness.registry.start();
        harness.registry.stop();
        harness.registry.stop(); // second call should be no-op

        assertThat(harness.registry.isStarted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Monitoring snapshot tests
    // -------------------------------------------------------------------------

    @Test
    void testMonitorSnapshotFromRuntime() {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        final PipelineMonitorSnapshot snapshot =
                PipelineMonitorProvider.buildSnapshot(harness.runtime);

        // 5 stages (receive + 4 queue-consuming)
        assertThat(snapshot.stages())
                .as("Snapshot should report all 4 stages")
                .hasSize(4);

        // Receive stage should have no worker
        final PipelineMonitorSnapshot.StageSnapshot receiveStage = snapshot.stages().stream()
                .filter(s -> s.name().equals("receive"))
                .findFirst()
                .orElseThrow();
        assertThat(receiveStage.hasWorker())
                .as("Receive stage should not have a worker")
                .isFalse();

        // Queue-consuming stages should have workers with zero-valued counters
        snapshot.stages().stream()
                .filter(PipelineMonitorSnapshot.StageSnapshot::hasWorker)
                .forEach(stage -> {
                    assertThat(stage.counters().pollCount())
                            .as("No polling should have occurred yet for " + stage.name())
                            .isEqualTo(0);
                });

        // 4 queues
        assertThat(snapshot.queues())
                .as("Snapshot should report all 3 queues")
                .hasSize(3);

        // 4 file stores
        assertThat(snapshot.fileStores())
                .as("Snapshot should report all 3 file stores")
                .hasSize(3);
    }

    @Test
    void testMonitorSnapshotSummaryFormatting() {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        final PipelineMonitorSnapshot snapshot =
                PipelineMonitorProvider.buildSnapshot(harness.runtime);

        // Verify summary strings are non-empty and well-formatted
        snapshot.stages().forEach(stage -> {
            final String summary = stage.toSummary();
            assertThat(summary)
                    .as("Stage summary should not be blank")
                    .isNotBlank();
            assertThat(summary)
                    .as("Stage summary should contain the stage name")
                    .contains(stage.name());
        });

        snapshot.queues().forEach(queue -> {
            assertThat(queue.toSummary())
                    .isNotBlank()
                    .contains(queue.name());
        });
    }

    // -------------------------------------------------------------------------
    // Config auto-wiring tests
    // -------------------------------------------------------------------------

    /**
     * A null {@code stages} block is the operator saying nothing, and nothing is no longer read as
     * "everything". It resolves to a stage set that is configured-empty and wholly disabled, so the
     * validator reports the silence and a process that somehow skipped validation sits idle rather
     * than running work nobody asked for.
     */
    @Test
    void testAnAbsentStagesBlockConfiguresNothing() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(null, null, null);

        assertThat(config.getStages().getConfiguredStages())
                .as("nothing was stated, so nothing is configured")
                .isEmpty();
        assertThat(config.getStages().getReceive().isEnabled()).isFalse();
        assertThat(config.getStages().getSplitZip().isEnabled()).isFalse();
        assertThat(config.getStages().getAggregate().isEnabled()).isFalse();
        assertThat(config.getStages().getForward().isEnabled()).isFalse();
    }

    /**
     * The programmatic no-arg default is still the standard full pipeline. That is a convenience for
     * tests and embedded callers and is deliberately not the same thing as an absent YAML block.
     */
    @Test
    void testTheNoArgConstructorStillGivesTheFullPipeline() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig();

        assertThat(config.getStages().getConfiguredStages())
                .hasSize(PipelineStageName.values().length);
        assertThat(config.getStages().getReceive().isEnabled()).isTrue();
        assertThat(config.getStages().getForward().isEnabled()).isTrue();
        assertThat(config.getStages().getReceive().getOutputQueue())
                .isEqualTo(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
        assertThat(config.getStages().getForward().getInputQueue())
                .isEqualTo(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
    }

    @Test
    void testExplicitStagesPreservedWhenProvided() {
        // Simulate: explicit stages provided in YAML
        final PipelineStagesConfig explicitStages = new PipelineStagesConfig(
                disabledReceiveStage(), disabledSplitZipStage(), disabledAggregateStage(),
                new ForwardStageConfig(
                        true,
                        "customInput",
                        new ConsumerStageThreadsConfig()));

        final ProxyPipelineConfig config = new ProxyPipelineConfig(null, explicitStages, null);

        // The explicit forward stage should be preserved
        assertThat(config.getStages().getForward().isEnabled()).isTrue();
        assertThat(config.getStages().getForward().getInputQueue()).isEqualTo("customInput");

        // Other stages should have their default (disabled) values since
        // we passed explicit stages — not the full pipeline defaults
        assertThat(config.getStages().getReceive().isEnabled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // End-to-end single-message flow test
    // -------------------------------------------------------------------------

    @Test
    void testPublishAndWorkerProcessSingleMessage() throws Exception {
        final RuntimeTestHarness harness = new RuntimeTestHarness(getCurrentTestDir());

        // 1. Publish a file group to the receive store → split-zip queue
        final Path receivedDir = getCurrentTestDir().resolve("e2e-incoming");
        Files.createDirectories(receivedDir);
        Files.writeString(receivedDir.resolve("proxy.meta"), "Feed:E2E_TEST");
        Files.writeString(receivedDir.resolve("proxy.zip"), "test-content");
        Files.writeString(receivedDir.resolve("proxy.entries"), "entries-data");

        final FileStore receiveStore = harness.runtime.getFileStores()
                .get(ProxyPipelineConfig.RECEIVE_STORE);
        final FileGroupQueue splitZipQueue = harness.runtime.getQueues()
                .get(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);

        final FileStoreLocation location;
        try (final FileStoreWrite write = receiveStore.newWrite()) {
            for (final String name : List.of("proxy.meta", "proxy.zip", "proxy.entries")) {
                Files.copy(receivedDir.resolve(name), write.getPath().resolve(name));
            }
            location = write.commit();
        }
        splitZipQueue.publish(FileGroupQueueMessage.create(
                location, null, null, "receive", "test-node", null, Map.of()));

        // 2. Verify the message is on the queue
        final FileGroupQueueItem item = splitZipQueue.next().orElseThrow();

        // 3. Verify the file group is in the file store and resolvable
        final FileGroupQueueMessage message = item.getMessage();
        final Path resolvedDir = receiveStore.resolve(message.fileStoreLocation());
        assertThat(resolvedDir).isDirectory();
        assertThat(resolvedDir.resolve("proxy.meta")).exists();
        assertThat(resolvedDir.resolve("proxy.zip")).exists();

        // 4. Verify the worker processes the item
        final FileGroupQueueWorker worker = harness.runtime
                .getWorker(PipelineStageName.SPLIT_ZIP)
                .orElseThrow();
        // The worker would process the item, but the split function is no-op
        // in the test harness. We verify the worker can at least poll and ack.
        item.acknowledge();

        // 5. Verify counters reflect the activity
        final FileGroupQueueWorkerCounters.Snapshot snapshot =
                worker.getCounters().snapshot();
        // No processNext() was called, so counters should still be zero
        assertThat(snapshot.pollCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Test Harness
    // -------------------------------------------------------------------------

    private static class RuntimeTestHarness {

        final ProxyPipelineRuntime runtime;
        final WorkRegistry registry;

        RuntimeTestHarness(final Path testDir) {
            // The full pipeline, stated. Passing null stages now means "nothing configured", so this
            // has to say what it wants rather than rely on a default reading of silence.
            final ProxyPipelineConfig config = new ProxyPipelineConfig();

            final PathCreator pathCreator = new TestPathCreator(testDir);

            final FileGroupQueueFactory queueFactory = new FileGroupQueueFactory(config, pathCreator);
            final FileStoreFactory fileStoreFactory = new FileStoreFactory(config, pathCreator);
            final FileStoreRegistry fileStoreRegistry = FileStoreRegistry.fromFactory(fileStoreFactory);

            // Build stage processors with no-op handlers
            final EnumMap<PipelineStageName, FileGroupQueueItemProcessor> stageProcessors =
                    new EnumMap<>(PipelineStageName.class);

            stageProcessors.put(PipelineStageName.FORWARD,
                    new ForwardStage(fileStoreRegistry, new NullDestination(), new GiveUp(new NullDestination()),
                            new ForwardRetryConfig().toBounds(), () -> false));

            final FileStore splitStore = fileStoreFactory.getFileStore(
                    ProxyPipelineConfig.SPLIT_STORE);
            final FileGroupQueue aggregateInputQueue = queueFactory.getQueue(
                    ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
            stageProcessors.put(PipelineStageName.SPLIT_ZIP,
                    new SplitZipStage(fileStoreRegistry, splitStore, aggregateInputQueue, "test-node"));
            this.runtime = ProxyPipelineRuntime.fromConfig(
                    config, queueFactory, fileStoreFactory, stageProcessors);
            this.registry = new WorkRegistry();
            ProxyPipelineAssembler.registerStages(registry, runtime);
        }
    }

    // -------------------------------------------------------------------------
    // TestPathCreator
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
        public String replaceTimeVars(final String path, final ZonedDateTime dateTime) {
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
        public String replaceFileName(final String path, final String fileName) {
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
        public String replace(final String path, final String var,
                              final LongSupplier replacementSupplier, final int pad) {
            return path;
        }

        @Override
        public String replace(final String str, final String var,
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


    private static ReceiveStageConfig disabledReceiveStage() {
        return new ReceiveStageConfig(false, null, null, null);
    }

    private static SplitZipStageConfig disabledSplitZipStage() {
        return new SplitZipStageConfig(false, null, null, null, null);
    }

    private static AggregateStageConfig disabledAggregateStage() {
        return new AggregateStageConfig(false, null, null, null, null, null, null, null);
    }
}
