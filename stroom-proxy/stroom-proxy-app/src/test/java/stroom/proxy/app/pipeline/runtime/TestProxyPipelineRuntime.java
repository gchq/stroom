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

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.PipelineValidationException;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorkerResult;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageThreadsConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestProxyPipelineRuntime extends StroomUnitTest {

    @Test
    void testFromConfigWithDefaultConfigCreatesFullRuntime() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig();
        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);

        assertThat(runtime.getStages()).hasSize(4);
        assertThat(runtime.getQueues()).hasSize(3);
        assertThat(runtime.getFileStores()).hasSize(3);

        assertThat(runtime.isStageEnabled(PipelineStageName.RECEIVE)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.SPLIT_ZIP)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.AGGREGATE)).isTrue();
        assertThat(runtime.isStageEnabled(PipelineStageName.FORWARD)).isTrue();

        assertThat(runtime.getStage(PipelineStageName.RECEIVE)).isPresent();
        assertThat(runtime.streamStages().toList()).hasSize(4);
    }

    @Test
    void testFromConfigBuildsReceiveRuntime() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);

        assertThat(runtime.getStages()).containsOnlyKeys(PipelineStageName.RECEIVE);
        assertThat(runtime.isStageEnabled(PipelineStageName.RECEIVE)).isTrue();

        final ProxyPipelineRuntime.RuntimeStage receiveStage =
                runtime.getStage(PipelineStageName.RECEIVE).orElseThrow();

        assertThat(receiveStage.stageName()).isEqualTo(PipelineStageName.RECEIVE);
        assertThat(receiveStage.getConfigName()).isEqualTo("receive");
        assertThat(receiveStage.hasInputQueue()).isFalse();
        assertThat(receiveStage.hasOutputQueue()).isTrue();
        assertThat(receiveStage.hasSplitZipQueue()).isTrue();
        assertThat(receiveStage.hasFileStore()).isTrue();
        assertThat(receiveStage.getThreads()).isNull();

        assertThat(receiveStage.getOutputQueue().orElseThrow().getName())
                .isEqualTo(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);
        assertThat(receiveStage.getSplitZipQueue().orElseThrow().getName())
                .isEqualTo(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);
        assertThat(receiveStage.getFileStore().orElseThrow().getName())
                .isEqualTo(ProxyPipelineConfig.RECEIVE_STORE);

        assertThat(runtime.getQueues())
                .containsOnlyKeys(
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);
        assertThat(runtime.getFileStores())
                .containsOnlyKeys(ProxyPipelineConfig.RECEIVE_STORE);
    }

    @Test
    void testFromConfigBuildsQueueSharingBetweenStages() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        new AggregateStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                ProxyPipelineConfig.AGGREGATE_STORE,
                                new AggregateStageThreadsConfig(3, null)),
                        disabledForwardStage()),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);

        assertThat(runtime.getStages())
                .containsOnlyKeys(
                        PipelineStageName.RECEIVE,
                        PipelineStageName.AGGREGATE);
        assertThat(runtime.getQueues())
                .containsOnlyKeys(
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
        assertThat(runtime.getFileStores())
                .containsOnlyKeys(
                        ProxyPipelineConfig.RECEIVE_STORE,
                        ProxyPipelineConfig.AGGREGATE_STORE);

        final ProxyPipelineRuntime.RuntimeStage receiveStage =
                runtime.getStage(PipelineStageName.RECEIVE).orElseThrow();
        final ProxyPipelineRuntime.RuntimeStage aggregateStage =
                runtime.getStage(PipelineStageName.AGGREGATE).orElseThrow();

        final FileGroupQueue receiveOutputQueue = receiveStage.getOutputQueue().orElseThrow();
        final FileGroupQueue aggregateInputQueue = aggregateStage.getInputQueue().orElseThrow();
        assertThat(aggregateInputQueue).isSameAs(receiveOutputQueue);
        assertThat(aggregateStage.getOutputQueue().orElseThrow().getName())
                .isEqualTo(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
        assertThat(aggregateStage.getThreads().getConsumerThreads()).isEqualTo(3);
    }

    @Test
    void testFromConfigBuildsFullLocalRuntime() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
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

        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);
        assertThat(runtime.getQueues())
                .containsOnlyKeys(
                        ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);
        assertThat(runtime.getFileStores())
                .containsOnlyKeys(
                        ProxyPipelineConfig.RECEIVE_STORE,
                        ProxyPipelineConfig.SPLIT_STORE,
                        ProxyPipelineConfig.AGGREGATE_STORE);

        assertThat(runtime.getStage(PipelineStageName.RECEIVE).orElseThrow().hasInputQueue()).isFalse();
        assertThat(runtime.getStage(PipelineStageName.RECEIVE).orElseThrow().hasOutputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.RECEIVE).orElseThrow().hasSplitZipQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.RECEIVE).orElseThrow().hasFileStore()).isTrue();

        assertThat(runtime.getStage(PipelineStageName.SPLIT_ZIP).orElseThrow().hasInputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.SPLIT_ZIP).orElseThrow().hasOutputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.SPLIT_ZIP).orElseThrow().hasFileStore()).isTrue();

        assertThat(runtime.getStage(PipelineStageName.AGGREGATE).orElseThrow().hasInputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.AGGREGATE).orElseThrow().hasOutputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.AGGREGATE).orElseThrow().hasFileStore()).isTrue();

        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasInputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasOutputQueue()).isFalse();
        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasFileStore()).isFalse();
    }

    @Test
    void testRuntimeQueueAndFileStoreLookup() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);

        assertThat(runtime.getQueue(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE)).isPresent();
        assertThat(runtime.getQueue(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE)).isEmpty();

        assertThat(runtime.getFileStore(ProxyPipelineConfig.RECEIVE_STORE)).isPresent();
        assertThat(runtime.getFileStore(ProxyPipelineConfig.AGGREGATE_STORE)).isEmpty();

        assertThat(runtime.streamQueueValues())
                .containsExactly(runtime.getQueue(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE).orElseThrow());
        assertThat(runtime.streamFileStoreValues())
                .containsExactly(runtime.getFileStore(ProxyPipelineConfig.RECEIVE_STORE).orElseThrow());
    }

    @Test
    void testRuntimeCreatesWorkersForQueueConsumingStagesWithProcessors() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
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

        final ProxyPipelineRuntime runtime = createRuntime(
                pipelineConfig,
                Map.of(
                        PipelineStageName.SPLIT_ZIP, item -> {
                        },
                        PipelineStageName.AGGREGATE, item -> {
                        },
                        PipelineStageName.FORWARD, item -> {
                        }));

        assertThat(runtime.getStage(PipelineStageName.RECEIVE).orElseThrow().hasWorker()).isFalse();
        assertThat(runtime.getWorker(PipelineStageName.RECEIVE)).isEmpty();

        assertThat(runtime.getStage(PipelineStageName.SPLIT_ZIP).orElseThrow().hasWorker()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.AGGREGATE).orElseThrow().hasWorker()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasWorker()).isTrue();
        assertThat(runtime.streamWorkers().toList()).hasSize(3);
    }

    @Test
    void testRuntimeDoesNotCreateWorkerWhenProcessorIsMissing() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = createRuntime(pipelineConfig);

        assertThat(runtime.getStage(PipelineStageName.FORWARD)).isPresent();
        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasInputQueue()).isTrue();
        assertThat(runtime.getStage(PipelineStageName.FORWARD).orElseThrow().hasWorker()).isFalse();
        assertThat(runtime.getWorker(PipelineStageName.FORWARD)).isEmpty();
        assertThat(runtime.getWorkers()).isEmpty();
        assertThat(runtime.streamWorkers().toList()).isEmpty();
    }

    @Test
    void testRuntimeWorkerProcessesInputQueueItem() throws IOException {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        disabledReceiveStage(),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        new ForwardStageConfig(
                                true,
                                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                                new ConsumerStageThreadsConfig())),
                defaultFileStores());
        final boolean[] processorCalled = new boolean[1];

        final ProxyPipelineRuntime runtime = createRuntime(
                pipelineConfig,
                Map.of(PipelineStageName.FORWARD, item -> {
                    processorCalled[0] = true;
                    assertThat(item.getMessage().messageId()).isEqualTo("file-group-1");
                }));

        final FileGroupQueue queue = runtime.getQueue(ProxyPipelineConfig.FORWARDING_INPUT_QUEUE).orElseThrow();
        final FileGroupQueueMessage message = createMessage(
                ProxyPipelineConfig.FORWARDING_INPUT_QUEUE,
                "file-group-1");

        queue.publish(message);

        final FileGroupQueueWorkerResult result =
                runtime.getWorker(PipelineStageName.FORWARD).orElseThrow().processNext();

        assertThat(processorCalled[0]).isTrue();
        assertThat(result.isProcessed()).isTrue();
        assertThat(result.message()).isEqualTo(message);
        assertThat(((LocalFileGroupQueue) queue).getApproximatePendingCount()).isZero();
        assertThat(((LocalFileGroupQueue) queue).getApproximateInFlightCount()).isZero();
    }

    @Test
    void testRuntimeCloseClosesInstantiatedQueues() throws IOException {
        final CloseTrackingQueueFactory queueFactory = new CloseTrackingQueueFactory();
        final FileStoreFactory fileStoreFactory = new FileStoreFactory(
                defaultFileStores(),
                new TestPathCreator(getCurrentTestDir()));
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                queueFactory,
                fileStoreFactory);

        assertThat(queueFactory.aggregateInputQueue.closed).isFalse();
        assertThat(queueFactory.splitZipInputQueue.closed).isFalse();

        runtime.close();

        assertThat(queueFactory.aggregateInputQueue.closed).isTrue();
        assertThat(queueFactory.splitZipInputQueue.closed).isTrue();
    }

    @Test
    void testRuntimeCloseSuppressesSubsequentCloseFailures() {
        final CloseTrackingQueueFactory queueFactory = new CloseTrackingQueueFactory();
        queueFactory.aggregateInputQueue.closeException = new IOException("aggregate close failure");
        queueFactory.splitZipInputQueue.closeException = new IOException("splitZip close failure");

        final FileStoreFactory fileStoreFactory = new FileStoreFactory(
                defaultFileStores(),
                new TestPathCreator(getCurrentTestDir()));
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                defaultQueues(),
                new PipelineStagesConfig(
                        new ReceiveStageConfig(
                                true,
                                ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                                ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                                ProxyPipelineConfig.RECEIVE_STORE),
                        disabledSplitZipStage(),
                        disabledAggregateStage(),
                        disabledForwardStage()),
                defaultFileStores());

        final ProxyPipelineRuntime runtime = ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                queueFactory,
                fileStoreFactory);

        assertThatThrownBy(runtime::close)
                .isInstanceOf(IOException.class)
                .satisfies(error -> {
                    // Close order of queues is not guaranteed by Map.copyOf(),
                    // so either exception could be primary.  Assert both
                    // messages are present across primary + suppressed.
                    assertThat(error.getSuppressed()).hasSize(1);

                    final Set<String> allMessages = new HashSet<>();
                    allMessages.add(error.getMessage());
                    for (final Throwable suppressed : error.getSuppressed()) {
                        allMessages.add(suppressed.getMessage());
                    }
                    assertThat(allMessages).containsExactlyInAnyOrder(
                            "aggregate close failure",
                            "splitZip close failure");
                });

        assertThat(queueFactory.aggregateInputQueue.closed).isFalse();
        assertThat(queueFactory.splitZipInputQueue.closed).isFalse();
    }

    private ProxyPipelineRuntime createRuntime(final ProxyPipelineConfig pipelineConfig) {
        return createRuntime(pipelineConfig, Map.of());
    }

    private ProxyPipelineRuntime createRuntime(
            final ProxyPipelineConfig pipelineConfig,
            final Map<PipelineStageName, FileGroupQueueItemProcessor> processors) {
        final TestPathCreator pathCreator = new TestPathCreator(getCurrentTestDir());
        return ProxyPipelineRuntime.fromConfig(
                pipelineConfig,
                new FileGroupQueueFactory(pipelineConfig, pathCreator),
                new FileStoreFactory(pipelineConfig, pathCreator),
                processors);
    }

    private FileGroupQueueMessage createMessage(final String queueName,
                                                final String fileGroupId) {
        return new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                fileGroupId,
                FileStoreLocation.filesystem(
                        ProxyPipelineConfig.AGGREGATE_STORE,
                        getCurrentTestDir().resolve("store").resolve(fileGroupId)),
                null,
                null,
                "aggregate",
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-" + fileGroupId,
                Map.of("feed", "TEST_FEED"));
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

    private static final class CloseTrackingQueueFactory extends FileGroupQueueFactory {

        private final CloseTrackingQueue splitZipInputQueue =
                new CloseTrackingQueue(ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE);
        private final CloseTrackingQueue aggregateInputQueue =
                new CloseTrackingQueue(ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE);

        private CloseTrackingQueueFactory() {
            super(defaultQueues(), new TestPathCreator(Path.of("/tmp")));
        }

        @Override
        public FileGroupQueue getQueue(final String queueName) {
            return switch (queueName) {
                case ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE -> splitZipInputQueue;
                case ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE -> aggregateInputQueue;
                default -> throw new IllegalArgumentException("Unexpected queue name " + queueName);
            };
        }
    }

    private static final class CloseTrackingQueue implements FileGroupQueue {

        private final String name;
        private boolean closed;
        private IOException closeException;

        private CloseTrackingQueue(final String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public QueueType getType() {
            return QueueType.LOCAL_FILESYSTEM;
        }

        @Override
        public void publish(final FileGroupQueueMessage message) {
            throw new UnsupportedOperationException("publish is not used by these tests");
        }

        @Override
        public Optional<FileGroupQueueItem> next(final Duration maxWait) {
            throw new UnsupportedOperationException("next is not used by these tests");
        }

        @Override
        public void close() throws IOException {
            if (closeException != null) {
                throw closeException;
            }
            closed = true;
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


    private static ReceiveStageConfig disabledReceiveStage() {
        return new ReceiveStageConfig(false, null, null, null);
    }

    private static SplitZipStageConfig disabledSplitZipStage() {
        return new SplitZipStageConfig(false, null, null, null, null);
    }

    private static AggregateStageConfig disabledAggregateStage() {
        return new AggregateStageConfig(false, null, null, null, null, null, null, null);
    }

    private static ForwardStageConfig disabledForwardStage() {
        return new ForwardStageConfig(false, null, null);
    }
}
