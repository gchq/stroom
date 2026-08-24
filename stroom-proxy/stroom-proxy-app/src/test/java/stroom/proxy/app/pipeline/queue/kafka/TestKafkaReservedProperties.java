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

package stroom.proxy.app.pipeline.queue.kafka;

import stroom.proxy.app.pipeline.config.PipelineValidationIssue;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka properties the implementation depends on must not be silently overridable.
 * <p>
 * User-supplied {@code consumer:}/{@code producer:} entries were previously applied
 * last, so an override of {@code max.poll.records} took effect - and because
 * {@code next()} returns one record per poll and discards the rest of the batch,
 * anything above 1 silently skipped records until the consumer restarted or
 * rebalanced. Weakening {@code acks} was likewise accepted, letting a publish report
 * success before the record was durably replicated.
 * </p>
 */
class TestKafkaReservedProperties {

    private final ProxyPipelineConfigValidator validator = new ProxyPipelineConfigValidator();

    private static QueueDefinition kafkaQueue(final Map<String, String> producerConfig,
                                              final Map<String, String> consumerConfig) {
        return new QueueDefinition(
                QueueType.KAFKA,
                null,
                "proxy-topic",
                "localhost:9092",
                producerConfig,
                consumerConfig,
                null,
                null,
                null);
    }

    private List<String> validateQueue(final QueueDefinition definition) {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                Map.of("kafkaQueue", definition),
                ProxyPipelineConfig.defaultFullPipelineStages(),
                null);

        final PipelineValidationResult result = validator.validate(config);
        return result.getErrors()
                .stream()
                .filter(issue -> ProxyPipelineConfigValidator
                        .CODE_QUEUE_RESERVED_PROPERTY.equals(issue.code()))
                .map(PipelineValidationIssue::message)
                .toList();
    }

    @Test
    void testOverridingMaxPollRecordsIsRejected() {
        final List<String> errors = validateQueue(kafkaQueue(
                null,
                Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .contains(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)
                .contains("consumer")
                .contains("kafkaQueue");
    }

    @Test
    void testOverridingAutoCommitIsRejected() {
        assertThat(validateQueue(kafkaQueue(
                null,
                Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"))))
                .hasSize(1);
    }

    @Test
    void testOverridingDeserialiserIsRejected() {
        assertThat(validateQueue(kafkaQueue(
                null,
                Map.of(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "com.example.Other"))))
                .hasSize(1);
    }

    @Test
    void testWeakeningProducerAcksIsRejected() {
        final List<String> errors = validateQueue(kafkaQueue(
                Map.of(ProducerConfig.ACKS_CONFIG, "0"),
                null));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .contains(ProducerConfig.ACKS_CONFIG)
                .contains("producer");
    }

    @Test
    void testEveryReservedPropertyIsReported() {
        final Map<String, String> allReservedConsumer = KafkaFileGroupQueue.RESERVED_CONSUMER_PROPERTIES
                .stream()
                .collect(java.util.stream.Collectors.toMap(k -> k, k -> "x"));

        assertThat(validateQueue(kafkaQueue(null, allReservedConsumer)))
                .hasSize(KafkaFileGroupQueue.RESERVED_CONSUMER_PROPERTIES.size());
    }

    @Test
    void testLegitimateOverridesAreStillAllowed() {
        // group.id, auto.offset.reset and transport settings remain tunable.
        final List<String> errors = validateQueue(kafkaQueue(
                Map.of("compression.type", "lz4"),
                Map.of(
                        ConsumerConfig.GROUP_ID_CONFIG, "my-group",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                        "security.protocol", "SASL_SSL",
                        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "600000")));

        assertThat(errors).isEmpty();
    }

    @Test
    void testNoOverridesIsValid() {
        assertThat(validateQueue(kafkaQueue(null, null))).isEmpty();
    }

    @Test
    void testReservedConsumerPropertiesWinEvenIfValidationIsBypassed() {
        // Belt and braces: validation rejects these, but the built client must still
        // be correct if it is ever constructed without validating.
        final Properties props = KafkaFileGroupQueue.buildConsumerProperties(
                "forwardingInput",
                kafkaQueue(null, Map.of(
                        ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500",
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "com.example.Other",
                        ConsumerConfig.GROUP_ID_CONFIG, "my-group")));

        assertThat(props.getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo("1");
        assertThat(props.getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo("false");
        assertThat(props.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(ByteArrayDeserializer.class.getName());

        // ...while a legitimate override still takes effect.
        assertThat(props.getProperty(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("my-group");
    }

    @Test
    void testReservedProducerPropertiesWinEvenIfValidationIsBypassed() {
        final Properties props = KafkaFileGroupQueue.buildProducerProperties(
                kafkaQueue(Map.of(
                        ProducerConfig.ACKS_CONFIG, "0",
                        "compression.type", "lz4"), null));

        assertThat(props.getProperty(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(props.getProperty("compression.type")).isEqualTo("lz4");
    }

    @Test
    void testDefaultsAreAppliedWhenNothingIsOverridden() {
        final Properties props = KafkaFileGroupQueue.buildConsumerProperties(
                "forwardingInput", kafkaQueue(null, null));

        assertThat(props.getProperty(ConsumerConfig.GROUP_ID_CONFIG))
                .isEqualTo(KafkaFileGroupQueue.DEFAULT_CONSUMER_GROUP_PREFIX + "forwardingInput");
        assertThat(props.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        assertThat(props.getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo("1");
    }
}
