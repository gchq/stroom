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

package stroom.statistics.impl.hbase.internal;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Not a test as such, just a means to evaluate different kafka producer config
 * settings.
 */
public class TestKafkaProducerAsync {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaProducerAsync.class);

    public static void main(final String[] args) {
        final Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
//        props.put("acks", "0");

        // Reduce the time blocked when the broker is not available.
        props.put("max.block.ms", 100);

        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        final KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);

        LOGGER.info("Sending records");
        final List<Future<RecordMetadata>> futures = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> {
                    final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                            "sourceTopic", "key" + i, Instant.now().toString());

                    final Future<RecordMetadata> sendFuture = kafkaProducer.send(
                            producerRecord,
                            (metadata, exception) ->
                                    LOGGER.info("Callback called, {}, {}", metadata, exception));

                    LOGGER.info("Sent record {} (to producer)", i);
                    return sendFuture;
                })
                .collect(Collectors.toList());
        LOGGER.info("Sent all records} (to producer)");

//        Thread.sleep(1_000);

        LOGGER.info("Getting futures");
        futures.forEach(recordMetadataFuture -> {
            try {
                recordMetadataFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.info("Future completed");

        LOGGER.info("Closing producer");
        kafkaProducer.close();
        LOGGER.info("Producer closed");
    }
}
