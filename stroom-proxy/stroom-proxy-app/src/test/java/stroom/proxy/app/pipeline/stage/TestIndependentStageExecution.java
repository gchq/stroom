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

package stroom.proxy.app.pipeline.stage;


import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import static stroom.proxy.app.pipeline.config.TestStageConfigFactory.*;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.runtime.FileGroupQueueFactory;
import stroom.proxy.app.pipeline.runtime.FileStoreFactory;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineLifecycle;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineRuntime;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests proving that individual pipeline stages can run independently
 * as standalone processes using local filesystem queues.
 * <p>
 * Each test configures only a subset of stages as enabled and verifies that
 * the runtime and lifecycle correctly assemble and run only those stages.
 * </p>
 */
class TestIndependentStageExecution extends StroomUnitTest {

    /**
     * Forward-only: only the forward stage is enabled.
     * The runtime should create only the forwarding input queue and worker.
     */
    @Test
    void testForwardOnlyConfig() throws IOException {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null, null, null, null,
                        forwardConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)),
                defaultFileStores());

        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());
        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                config,
                new FileGroupQueueFactory(config, pathCreator),
                new FileStoreFactory(config, pathCreator),
                Map.of(PipelineStageName.FORWARD, item -> {}));

        // Only forward stage should be enabled.
        assertThat(runtime.isStageEnabled(PipelineStageName.FORWARD)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.RECEIVE)).isFalse();
        assertThat(runtime.isStageEnabled(PipelineStageName.SPLIT_ZIP)).isFalse();
        assertThat(runtime.isStageEnabled(PipelineStageName.PRE_AGGREGATE)).isFalse();
        assertThat(runtime.isStageEnabled(PipelineStageName.AGGREGATE)).isFalse();

        // Should have one worker.
        assertThat(runtime.getWorkers()).hasSize(1);
        assertThat(runtime.getWorker(PipelineStageName.FORWARD)).isPresent();

        // Lifecycle should have one runner.
        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);
        assertThat(lifecycle.getRunners()).hasSize(1);
        assertThat(lifecycle.getRunners().get(0).getStageName()).isEqualTo(PipelineStageName.FORWARD);

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    /**
     * Aggregate + Forward: only aggregate and forward are enabled.
     * Simulates a deployment where receive/split/pre-aggregate run on
     * separate processes and this process handles the final stages.
     */
    @Test
    void testAggregateAndForwardConfig() throws IOException {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null, null, null,
                        aggregateConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE),
                        forwardConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)),
                defaultFileStores());

        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());
        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                config,
                new FileGroupQueueFactory(config, pathCreator),
                new FileStoreFactory(config, pathCreator),
                Map.of(
                        PipelineStageName.AGGREGATE, item -> {},
                        PipelineStageName.FORWARD, item -> {}));

        assertThat(runtime.getStages()).hasSize(2);
        assertThat(runtime.isStageEnabled(PipelineStageName.AGGREGATE)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.FORWARD)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.RECEIVE)).isFalse();

        // Both stages should have workers.
        assertThat(runtime.getWorkers()).hasSize(2);

        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);
        assertThat(lifecycle.getRunners()).hasSize(2);

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();
        lifecycle.stop();
    }

    /**
     * Receive-only: only the receive stage is enabled.
     * The receive stage is HTTP-driven (no input queue), so there should be
     * no workers or runners — just the output queue and file store.
     */
    @Test
    void testReceiveOnlyConfig() throws IOException {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        receiveConfig(
                                true,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new ReceiveStageThreadsConfig()),
                        null, null, null, null),
                defaultFileStores());

        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());
        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                config,
                new FileGroupQueueFactory(config, pathCreator),
                new FileStoreFactory(config, pathCreator));

        assertThat(runtime.isStageEnabled(PipelineStageName.RECEIVE)).isTrue();
        assertThat(runtime.getStages()).hasSize(1);
        assertThat(runtime.getWorkers()).isEmpty();

        // Output queue should still be created for the receive stage.
        assertThat(runtime.getQueue(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE)).isPresent();

        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);
        assertThat(lifecycle.getRunners()).isEmpty();
        lifecycle.start();
        lifecycle.stop();
    }

    /**
     * End-to-end: forward-only stage processes items from its input queue.
     * Simulates an externally-published queue message being consumed
     * by the independently-running forward stage.
     */
    @Test
    void testForwardOnlyProcessesExternallyPublishedMessages() throws Exception {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null, null, null, null,
                        forwardConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)),
                defaultFileStores());

        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());

        final CountDownLatch processedLatch = new CountDownLatch(2);
        final AtomicInteger processedCount = new AtomicInteger();

        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                config,
                new FileGroupQueueFactory(config, pathCreator),
                new FileStoreFactory(config, pathCreator),
                Map.of(PipelineStageName.FORWARD, item -> {
                    processedCount.incrementAndGet();
                    processedLatch.countDown();
                }));

        // Simulate external producer publishing to the forwarding queue.
        final FileGroupQueue forwardingQueue = runtime.getQueue(
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE).orElseThrow();

        forwardingQueue.publish(createMessage(forwardingQueue.getName(), "fg-ext-1"));
        forwardingQueue.publish(createMessage(forwardingQueue.getName(), "fg-ext-2"));

        // Start lifecycle and wait for processing.
        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(
                runtime, Duration.ofSeconds(5));
        lifecycle.start();

        final boolean allProcessed = processedLatch.await(5, TimeUnit.SECONDS);
        lifecycle.stop();

        assertThat(allProcessed).isTrue();
        assertThat(processedCount.get()).isEqualTo(2);
    }

    /**
     * Validation passes for independent stage configs — disabled stages
     * are not validated.
     */
    @Test
    void testValidationSkipsDisabledStages() {
        // Config with only forward enabled and no file stores for disabled stages.
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        null, null, null, null,
                        forwardConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)),
                defaultFileStores());

        final PipelineValidationResult result = new ProxyPipelineConfigValidator().validate(config);
        assertThat(result.isValid()).isTrue();
    }

    // --- helpers ---

    private FileGroupQueueMessage createMessage(final String queueName,
                                                 final String fileGroupId) {
        return FileGroupQueueMessage.create(
                "message-" + fileGroupId,
                queueName,
                fileGroupId,
                FileStoreLocation.localFileSystem(
                        ProxyPipelineConfig.AGGREGATE_STORE,
                        getCurrentTestDir().resolve("store").resolve(fileGroupId)),
                "aggregate",
                "test-node",
                Instant.now(),
                "trace-" + fileGroupId,
                Map.of());
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

    private static final class TestPathCreator implements PathCreator {
        private final Path root;

        private TestPathCreator(final Path root) {
            this.root = root;
        }

        @Override public String replaceTimeVars(final String path) { return path; }
        @Override public String replaceTimeVars(final String path, final ZonedDateTime dateTime) { return path; }
        @Override public String replaceSystemProperties(final String path) { return path; }
        @Override public Path toAppPath(final String pathString) {
            final Path path = Path.of(pathString);
            return path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
        }
        @Override public String replaceUUIDVars(final String path) { return path; }
        @Override public String replaceFileName(final String path, final String fileName) { return path; }
        @Override public String[] findVars(final String path) { return new String[0]; }
        @Override public boolean containsVars(final String path) { return false; }
        @Override public String replace(final String path, final String var, final LongSupplier replacementSupplier, final int pad) { return path; }
        @Override public String replace(final String str, final String var, final Supplier<String> replacementSupplier) { return str; }
        @Override public String replaceAll(final String path) { return path; }
        @Override public String replaceContextVars(final String path) { return path; }
    }
}
