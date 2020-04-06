/*
 * Copyright 2019 Crown Copyright
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
package stroom.kafka.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaConfigStore;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafkaConfig.shared.KafkaConfigDoc;

import org.apache.kafka.clients.producer.KafkaProducer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

@Singleton
class KafkaProducerFactoryImpl implements KafkaProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerFactoryImpl.class);

    private final KafkaConfigStore kafkaConfigStore;

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigStore kafkaConfigStore) {
        this.kafkaConfigStore = kafkaConfigStore;
    }

    @Override
    public Optional<KafkaProducer<String, String>> createProducer(final DocRef kafkaConfigRef) {
        if (kafkaConfigRef.getUuid() == null) {
            throw new RuntimeException("No Kafka config UUID has been defined, unable to send any events");
        }

        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigRef);

        final KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(getProperties(kafkaConfigDoc));
        return Optional.of(kafkaProducer);
    }

    void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
        // TODO need to flush all producers
//        super.shutdown();
    }

    public static Properties getProperties(KafkaConfigDoc doc){
        Properties properties = new Properties();
        if (doc.getData() != null && !doc.getData().isEmpty()) {
            StringReader reader = new StringReader(doc.getData());
            try {
                properties.load(reader);
            }catch (IOException ex){
                LOGGER.error("Unable to read kafka properties", ex);
            }
        }
        return properties;
    }

}
