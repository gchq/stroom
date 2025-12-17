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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.ProxyConfig;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class KafkaEventConsumer implements EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KafkaEventConsumer.class);

    private final KafkaEventConsumerConfig config;
    private final ProxyConfig proxyConfig;
    private final EventSerialiser eventSerialiser;
    private final KafkaProducer<byte[], byte[]> kafkaProducer;

    public KafkaEventConsumer(final KafkaEventConsumerConfig config,
                              final ProxyConfig proxyConfig,
                              final EventSerialiser eventSerialiser) {
        this.config = config;
        this.proxyConfig = proxyConfig;
        this.eventSerialiser = eventSerialiser;
        final Properties properties = new KafkaProducerProperties(config.getProducerConfig()).getProperties();

        try {
            LOGGER.debug(() -> "Creating KafkaProducer");
            kafkaProducer = new KafkaProducer<>(properties, new ByteArraySerializer(), new ByteArraySerializer());
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException("Error creating KafkaProducer " + e.getMessage(), e);
        }
    }

    private void add(final Properties properties, final Object key, final Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private byte[] getBytes(final String string) {
        if (string == null) {
            return new byte[0];
        }
        return string.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] createKey(final FeedKey feedKey) {
        final byte[] feedBytes = getBytes(feedKey.feed());
        final byte[] typeBytes = getBytes(feedKey.type());
        final byte[] arr = new byte[feedBytes.length + typeBytes.length + Integer.BYTES + Integer.BYTES];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
        byteBuffer.put(feedBytes);
        byteBuffer.put(typeBytes);
        byteBuffer.putInt(feedBytes.length);
        byteBuffer.putInt(typeBytes.length);
        return arr;
    }

    @Override
    public void consume(final AttributeMap attributeMap,
                        final UniqueId receiptId,
                        final String data) {
        try {
            final List<Header> headers = new ArrayList<>(attributeMap.size());
            attributeMap.forEach((k, v) -> {
                final Header header = new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8));
                headers.add(header);
            });

            final FeedKey feedKey = FeedKey.from(attributeMap);

            final String string = eventSerialiser.serialise(
                    receiptId,
                    feedKey,
                    attributeMap,
                    data);

            final String topic = config.getTopic();
            final Integer partition = null;
            final Long timestamp = System.currentTimeMillis();
            final byte[] key = createKey(FeedKey.from(attributeMap));
            final byte[] value = string.getBytes(StandardCharsets.UTF_8);

            final ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                    topic,
                    partition,
                    timestamp,
                    key,
                    value,
                    headers);

            final Future<RecordMetadata> sendFuture = kafkaProducer.send(record);
            final RecordMetadata recordMetadata = sendFuture.get();
            LOGGER.debug(() -> "Sent data: " + recordMetadata);
        } catch (final IOException | InterruptedException | ExecutionException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
