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
import stroom.kafka.pipeline.KafkaProducer;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafkaConfig.shared.KafkaConfigDoc;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class KafkaProducerFactoryImpl implements KafkaProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerFactoryImpl.class);

    private final KafkaConfigStore kafkaConfigStore;

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigStore kafkaConfigStore) {
        this.kafkaConfigStore = kafkaConfigStore;
    }

    @Override
    public Optional<KafkaProducer> createProducer(final DocRef kafkaConfigRef) {
        if (kafkaConfigRef.getUuid() == null) {
            throw new RuntimeException("No Kafka config UUID has been defined, unable to send any events");
        }

        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigRef);

        return Optional.of(new KafkaProducerImpl(kafkaConfigDoc));
    }

    void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
//        super.shutdown();
    }
}
