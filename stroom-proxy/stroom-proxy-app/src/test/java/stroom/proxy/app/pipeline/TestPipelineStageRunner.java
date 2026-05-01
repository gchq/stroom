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

package stroom.proxy.app.pipeline;

import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPipelineStageRunner extends StroomUnitTest {

    @Test
    void testStartAndStopWithEmptyQueue() throws IOException {
        final LocalFileGroupQueue queue = createQueue("empty-queue");
        final AtomicInteger processedCount = new AtomicInteger();

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> processedCount.incrementAndGet());

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                2,
                Duration.ofMillis(10),
                Duration.ofSeconds(1));

        assertThat(runner.isRunning()).isFalse();

        runner.start();
        assertThat(runner.isRunning()).isTrue();

        // Let threads run a few poll cycles on the empty queue.
        sleep(50);

        final boolean clean = runner.stop(Duration.ofSeconds(5));
        assertThat(clean).isTrue();
        assertThat(runner.isRunning()).isFalse();
        assertThat(processedCount.get()).isZero();
    }

    @Test
    void testRunnerProcessesQueueItems() throws Exception {
        final LocalFileGroupQueue queue = createQueue("process-queue");
        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicInteger processedCount = new AtomicInteger();

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {
                    processedCount.incrementAndGet();
                    latch.countDown();
                });

        // Publish 3 messages before starting the runner.
        for (int i = 1; i <= 3; i++) {
            queue.publish(createMessage(queue.getName(), "fg-" + i));
        }

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                1,
                Duration.ofMillis(10),
                Duration.ofSeconds(1));

        runner.start();

        final boolean allProcessed = latch.await(5, TimeUnit.SECONDS);
        runner.stop(Duration.ofSeconds(5));

        assertThat(allProcessed).isTrue();
        assertThat(processedCount.get()).isEqualTo(3);
    }

    @Test
    void testRunnerThreadCountMatchesConfig() throws IOException {
        final LocalFileGroupQueue queue = createQueue("thread-count-queue");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {});

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.PRE_AGGREGATE,
                worker,
                4,
                Duration.ofMillis(10),
                Duration.ofSeconds(1));

        assertThat(runner.getThreadCount()).isEqualTo(4);
        assertThat(runner.getStageName()).isEqualTo(PipelineStageName.PRE_AGGREGATE);

        runner.start();
        sleep(50);
        assertThat(runner.getActiveThreadCount()).isEqualTo(4);
        runner.stop(Duration.ofSeconds(5));
    }

    @Test
    void testStartIsIdempotent() throws IOException {
        final LocalFileGroupQueue queue = createQueue("idempotent-queue");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {});

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                1,
                Duration.ofMillis(10),
                Duration.ofSeconds(1));

        runner.start();
        runner.start(); // Should be a no-op.
        assertThat(runner.isRunning()).isTrue();
        runner.stop(Duration.ofSeconds(5));
    }

    @Test
    void testStopIsIdempotent() throws IOException {
        final LocalFileGroupQueue queue = createQueue("stop-idempotent");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {});

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                1,
                Duration.ofMillis(10),
                Duration.ofSeconds(1));

        runner.start();
        runner.stop(Duration.ofSeconds(5));
        // Second stop should be a no-op.
        final boolean clean = runner.stop(Duration.ofSeconds(5));
        assertThat(clean).isTrue();
    }

    @Test
    void testRejectsInvalidThreadCount() throws IOException {
        final LocalFileGroupQueue queue = createQueue("invalid-tc");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {});

        assertThatThrownBy(() -> new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                0,
                Duration.ofMillis(10),
                Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threadCount");
    }

    @Test
    void testRunnerContinuesAfterProcessingError() throws Exception {
        final LocalFileGroupQueue queue = createQueue("error-queue");
        final AtomicInteger callCount = new AtomicInteger();
        final CountDownLatch secondItemProcessed = new CountDownLatch(1);

        // Publish 2 messages.
        queue.publish(createMessage(queue.getName(), "fg-1"));
        queue.publish(createMessage(queue.getName(), "fg-2"));

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {
                    final int call = callCount.incrementAndGet();
                    if (call == 1) {
                        throw new IOException("Simulated processing error");
                    }
                    // Second item should still be processed.
                    secondItemProcessed.countDown();
                });

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                worker,
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10));  // Short error backoff for test.

        runner.start();

        final boolean processed = secondItemProcessed.await(5, TimeUnit.SECONDS);
        runner.stop(Duration.ofSeconds(5));

        assertThat(processed).isTrue();
        assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testLifecycleFromRuntime() throws IOException {
        final ProxyPipelineConfig pipelineConfig = createFullConfig();
        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());

        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                new FileGroupQueueFactory(pipelineConfig, pathCreator),
                new FileStoreFactory(pipelineConfig, pathCreator),
                Map.of(
                        PipelineStageName.SPLIT_ZIP, item -> {},
                        PipelineStageName.PRE_AGGREGATE, item -> {},
                        PipelineStageName.AGGREGATE, item -> {},
                        PipelineStageName.FORWARD, item -> {}));

        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);

        // 4 queue-consuming stages (receive has no worker).
        assertThat(lifecycle.getRunners()).hasSize(4);
        assertThat(lifecycle.isRunning()).isFalse();

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();

        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void testLifecycleWithNoWorkers() throws IOException {
        // Only receive stage enabled — no workers.
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig()),
                        null, null, null, null),
                defaultFileStores());

        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());
        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                new FileGroupQueueFactory(pipelineConfig, pathCreator),
                new FileStoreFactory(pipelineConfig, pathCreator));

        final ProxyPipelineLifecycle lifecycle = ProxyPipelineLifecycle.fromRuntime(runtime);
        assertThat(lifecycle.getRunners()).isEmpty();

        // Start/stop should be no-ops, not errors.
        lifecycle.start();
        lifecycle.stop();
    }

    // --- helpers ---

    private LocalFileGroupQueue createQueue(final String name) throws IOException {
        final Path queueDir = getCurrentTestDir().resolve("queues").resolve(name);
        Files.createDirectories(queueDir);
        return new LocalFileGroupQueue(name, queueDir);
    }

    private FileGroupQueueMessage createMessage(final String queueName,
                                                 final String fileGroupId) {
        return FileGroupQueueMessage.create(
                "message-" + fileGroupId,
                queueName,
                fileGroupId,
                FileStoreLocation.localFileSystem(
                        ProxyPipelineConfig.AGGREGATE_STORE,
                        getCurrentTestDir().resolve("store").resolve(fileGroupId)),
                "test",
                "test-node",
                Instant.now(),
                "trace-" + fileGroupId,
                Map.of());
    }

    private ProxyPipelineConfig createFullConfig() {
        return new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true, null,
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
                                null, null, null,
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

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

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
}
