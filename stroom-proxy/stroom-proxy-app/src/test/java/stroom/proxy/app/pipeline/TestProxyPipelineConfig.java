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

import stroom.proxy.app.ProxyConfig;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestProxyPipelineConfig extends StroomUnitTest {

    @Test
    void testDefaultPipelineConfigHasNamedLocalQueues() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig();

        assertThat(config.getQueues())
                .containsOnlyKeys(
                        ProxyPipelineConfig.SPLIT_ZIP_INPUT_QUEUE,
                        ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.AGGREGATE_INPUT_QUEUE,
                        ProxyPipelineConfig.FORWARDING_INPUT_QUEUE);

        assertThat(config.getQueues().values())
                .allSatisfy(queueDefinition ->
                        assertThat(queueDefinition.getType()).isEqualTo(QueueType.LOCAL_FILESYSTEM));
    }

    @Test
    void testDefaultPipelineConfigHasNamedFileStores() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig();

        assertThat(config.getFileStores())
                .containsOnlyKeys(
                        ProxyPipelineConfig.RECEIVE_STORE,
                        ProxyPipelineConfig.SPLIT_STORE,
                        ProxyPipelineConfig.PRE_AGGREGATE_STORE,
                        ProxyPipelineConfig.AGGREGATE_STORE);
    }

    @Test
    void testDefaultPipelineConfigHasDisabledStages() {
        final ProxyPipelineConfig config = new ProxyPipelineConfig();

        assertThat(config.getStages().getReceive().isEnabled()).isFalse();
        assertThat(config.getStages().getSplitZip().isEnabled()).isFalse();
        assertThat(config.getStages().getPreAggregate().isEnabled()).isFalse();
        assertThat(config.getStages().getAggregate().isEnabled()).isFalse();
        assertThat(config.getStages().getForward().isEnabled()).isFalse();
    }

    @Test
    void testProxyConfigIncludesDefaultPipelineConfig() {
        final ProxyConfig proxyConfig = new ProxyConfig();

        assertThat(proxyConfig.getPipelineConfig()).isNotNull();
        assertThat(proxyConfig.getPipelineConfig().getQueues())
                .containsKey(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE);
        assertThat(proxyConfig.getPipelineConfig().getFileStores())
                .containsKey(ProxyPipelineConfig.RECEIVE_STORE);
    }

    @Test
    void testProxyConfigBuilderAcceptsPipelineConfig() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                Map.of("customQueue", new QueueDefinition()),
                new PipelineStagesConfig(
                        new PipelineStageConfig(
                                true,
                                null,
                                "customQueue",
                                null,
                                ProxyPipelineConfig.RECEIVE_STORE,
                                new PipelineStageThreadsConfig(7, 2, 1)),
                        null,
                        null,
                        null,
                        null),
                Map.of(ProxyPipelineConfig.RECEIVE_STORE, new FileStoreDefinition("stores/receive")));

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pipelineConfig(pipelineConfig)
                .build();

        assertThat(proxyConfig.getPipelineConfig()).isSameAs(pipelineConfig);
        assertThat(proxyConfig.getPipelineConfig().getQueues()).containsOnlyKeys("customQueue");
        assertThat(proxyConfig.getPipelineConfig().getStages().getReceive().isEnabled()).isTrue();
        assertThat(proxyConfig.getPipelineConfig().getStages().getReceive().getOutputQueue()).isEqualTo("customQueue");
        assertThat(proxyConfig.getPipelineConfig().getStages().getReceive().getThreads().getMaxConcurrentReceives())
                .isEqualTo(7);
    }

    @Test
    void testQueueDefinitionDefaultsToLocalFilesystem() {
        final QueueDefinition queueDefinition = new QueueDefinition();

        assertThat(queueDefinition.getType()).isEqualTo(QueueType.LOCAL_FILESYSTEM);
        assertThat(queueDefinition.getPath()).isNull();
        assertThat(queueDefinition.getProducerConfig()).isEmpty();
        assertThat(queueDefinition.getConsumerConfig()).isEmpty();
    }

    @Test
    void testQueueDefinitionCopiesConfigMaps() {
        final Map<String, String> producerConfig = Map.of("acks", "all");
        final Map<String, String> consumerConfig = Map.of("groupId", "proxy-workers");

        final QueueDefinition queueDefinition = new QueueDefinition(
                QueueType.KAFKA,
                null,
                "proxy-topic",
                "localhost:9092",
                producerConfig,
                consumerConfig,
                null,
                null,
                null);

        assertThat(queueDefinition.getType()).isEqualTo(QueueType.KAFKA);
        assertThat(queueDefinition.getTopic()).isEqualTo("proxy-topic");
        assertThat(queueDefinition.getBootstrapServers()).isEqualTo("localhost:9092");
        assertThat(queueDefinition.getProducerConfig()).containsEntry("acks", "all");
        assertThat(queueDefinition.getConsumerConfig()).containsEntry("groupId", "proxy-workers");
    }

    @Test
    void testQueueDefinitionValidationHelpers() {
        assertThat(new QueueDefinition(
                QueueType.KAFKA,
                null,
                "proxy-topic",
                "localhost:9092",
                null,
                null,
                null,
                null,
                null).isKafkaConfigValid())
                .isTrue();

        assertThat(new QueueDefinition(
                QueueType.KAFKA,
                null,
                null,
                "localhost:9092",
                null,
                null,
                null,
                null,
                null).isKafkaConfigValid())
                .isFalse();

        assertThat(new QueueDefinition(
                QueueType.SQS,
                null,
                null,
                null,
                null,
                null,
                "https://sqs.eu-west-2.amazonaws.com/123456789012/proxy",
                null,
                null).isSqsConfigValid())
                .isTrue();
    }

    @Test
    void testFileGroupQueueFactoryCreatesLocalFilesystemQueueForLogicalName() {
        final QueueDefinition queueDefinition = new QueueDefinition(
                QueueType.LOCAL_FILESYSTEM,
                "queues/pre-aggregate",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                Map.of("preAggregateInput", queueDefinition),
                new PipelineStagesConfig(),
                Map.of());
        final FileGroupQueueFactory factory = new FileGroupQueueFactory(
                pipelineConfig,
                new TestPathCreator(getCurrentTestDir()));

        final FileGroupQueue queue = factory.getQueue("preAggregateInput");

        assertThat(queue.getName()).isEqualTo("preAggregateInput");
        assertThat(queue.getType()).isEqualTo(QueueType.LOCAL_FILESYSTEM);
        assertThat(queue).isInstanceOf(LocalFileGroupQueue.class);
        assertThat(((LocalFileGroupQueue) queue).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("queues/pre-aggregate").normalize());
    }

    @Test
    void testFileGroupQueueFactoryDerivesLocalFilesystemPathWhenNoPathConfigured() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(
                Map.of("forwardingInput", new QueueDefinition()),
                new PipelineStagesConfig(),
                Map.of());
        final FileGroupQueueFactory factory = new FileGroupQueueFactory(
                pipelineConfig,
                new TestPathCreator(getCurrentTestDir()));

        final FileGroupQueue queue = factory.getQueue("forwardingInput");

        assertThat(queue).isInstanceOf(LocalFileGroupQueue.class);
        assertThat(((LocalFileGroupQueue) queue).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("data/pipeline/queues/forwardingInput").normalize());
    }

    @Test
    void testFileGroupQueueFactoryRejectsUnknownQueueName() {
        final FileGroupQueueFactory factory = new FileGroupQueueFactory(
                new ProxyPipelineConfig(),
                new TestPathCreator(getCurrentTestDir()));

        assertThatThrownBy(() -> factory.getQueue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void testFileGroupQueueFactoryRejectsBlankQueueName() {
        final FileGroupQueueFactory factory = new FileGroupQueueFactory(
                new ProxyPipelineConfig(),
                new TestPathCreator(getCurrentTestDir()));

        assertThatThrownBy(() -> factory.getQueue(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueName");
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
}
