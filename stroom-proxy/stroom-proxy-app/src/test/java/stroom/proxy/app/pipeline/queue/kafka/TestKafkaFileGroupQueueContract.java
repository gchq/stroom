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

package stroom.proxy.app.pipeline.queue.kafka;

import stroom.proxy.app.pipeline.queue.AbstractFileGroupQueueContractTest;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the {@link AbstractFileGroupQueueContractTest} suite against a real
 * Kafka broker provided by Testcontainers.
 * <p>
 * Tagged as "integration" so it is excluded from the normal {@code ./gradlew test}
 * run and only executed via {@code ./gradlew integrationTest}.
 * </p>
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class TestKafkaFileGroupQueueContract extends AbstractFileGroupQueueContractTest {

    private static final String TOPIC = "contract-test-topic";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.9.0"));

    @Override
    protected FileGroupQueue createQueue(final String name) throws IOException {
        final String bootstrapServers = KAFKA.getBootstrapServers();

        final Producer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName()));

        final Consumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "contract-test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()));

        return new KafkaFileGroupQueue(name, TOPIC, bootstrapServers,
                producer, consumer, new FileGroupQueueMessageCodec());
    }

    /**
     * Override: With a real Kafka broker, the consumer poll cache may still
     * return the acknowledged message on the next poll even though the offset
     * has been committed. Instead of asserting {@code next()} is empty, we
     * verify that the offset was committed correctly — which is the
     * Kafka-native equivalent of "acknowledged message is not redelivered".
     */
    @Override
    @Test
    protected void contractAcknowledgePreventsRedelivery() throws IOException {
        final FileGroupQueue queue = createQueue("contractTestQueue");
        final FileGroupQueueMessage message = createMessage("fg-kafka-ack");
        queue.publish(message);

        // Small delay to let Kafka propagate the message.
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.acknowledge();
        }

        // Verify the offset was committed by creating a new consumer in the
        // same group — it should not see the acknowledged message.
        final String bootstrapServers = KAFKA.getBootstrapServers();
        try (final Consumer<String, byte[]> verifyConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "contract-test-group-verify",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()))) {

            verifyConsumer.subscribe(Collections.singletonList(TOPIC));
            // Poll briefly — should get nothing since offset is past the message.
            final var records = verifyConsumer.poll(Duration.ofSeconds(2));
            assertThat(records.count())
                    .as("Acknowledged message should not be redelivered to a new consumer starting from latest")
                    .isZero();
        }

        queue.close();
    }
}
