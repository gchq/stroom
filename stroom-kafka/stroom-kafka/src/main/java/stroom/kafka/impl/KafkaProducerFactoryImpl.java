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

import com.codahale.metrics.health.HealthCheck;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaConfigStore;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Class to provide shared {@link KafkaProducer} instances wrapped inside a {@link KafkaProducerSupplier}.
 * We want one {@link KafkaProducer} per {@link KafkaConfigDoc}, however the config in a {@link KafkaConfigDoc}
 * can change at runtime so on each request for a {@link KafkaProducerSupplier} we need to check if the
 * {@link KafkaConfigDoc} has changed since the time we created it. New calls to
 * {@link KafkaProducerFactory#getSupplier(DocRef)} will always get a {@link KafkaProducerSupplier} for the
 * latest config. {@link KafkaProducer}
 */
@Singleton
class KafkaProducerFactoryImpl implements KafkaProducerFactory, HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerFactoryImpl.class);

    private final KafkaConfigStore kafkaConfigStore;

    // Keyed on uuid, represents the current KP for each UUID. These are the KPs that are given out to
    // new calls to getSupplier()
    private final ConcurrentMap<String, KafkaProducerSupplier> currentProducerSuppliersMap = new ConcurrentHashMap<>();
    // Keyed on uuid|version so can hold one or more KPs for each uuid if the config has changed over
    // time. Once clients are no longer using the superseded KPs they will be removed.
    private final ConcurrentMap<KafkaProducerSupplierKey, KafkaProducerSupplier> allProducerSuppliersMap = new ConcurrentHashMap<>();

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigStore kafkaConfigStore) {
        this.kafkaConfigStore = kafkaConfigStore;
    }

    public KafkaProducerSupplier getSupplier(final DocRef kafkaConfigDocRef) {
        Objects.requireNonNull(kafkaConfigDocRef);
        Objects.requireNonNull(kafkaConfigDocRef.getUuid(),
                "No Kafka config UUID has been defined, unable to send any events");

        // TODO could do with caching the docs to save the db lookups all the time
        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigDocRef);
        final KafkaProducerSupplier kafkaProducerSupplier;

        if (kafkaConfigDoc == null) {
            // No doc for this docref so return an empty supplier
            kafkaProducerSupplier = KafkaProducerSupplier.empty();
        } else {
            final KafkaProducerSupplierKey key = new KafkaProducerSupplierKey(kafkaConfigDoc);

            kafkaProducerSupplier = currentProducerSuppliersMap.compute(
                    kafkaConfigDoc.getUuid(),
                    (k, existingValue) -> {
                        final KafkaProducerSupplier producerSupplier;
                        if (existingValue == null) {
                            producerSupplier = createProducerSupplier(
                                    kafkaConfigDoc, kafkaConfigDocRef, key);
                            producerSupplier.incrementUseCount();
                            allProducerSuppliersMap.put(key, producerSupplier);
                        } else {
                            // we already have one so check if it should be current
                            if (key.equals(existingValue.getKafkaProducerSupplierKey())) {
                                // is up to date
                                producerSupplier = existingValue;
                            } else {
                                // needs to be superseded
                                existingValue.markSuperseded();

                                KafkaProducerSupplier newProducerSupplier = createProducerSupplier(
                                        kafkaConfigDoc, kafkaConfigDocRef, key);
                                newProducerSupplier.incrementUseCount();
                                allProducerSuppliersMap.put(key, newProducerSupplier);

                                // swap out the existing current supplier for our new one
                                producerSupplier = newProducerSupplier;
                            }
                        }
                        return producerSupplier;
                    });
        }

        return kafkaProducerSupplier;
    }

    private KafkaProducerSupplier createProducerSupplier(
            final KafkaConfigDoc kafkaConfigDoc,
            final DocRef kafkaConfigDocRef,
            final KafkaProducerSupplierKey key) {

        final Properties producerProperties = getProperties(kafkaConfigDoc);
        final KafkaProducer<String, byte[]> kafkaProducer;
        try {
            // For flexibility we always use a byte[] as the msg value.  This means the same producer
            // can cope with different flavours of data and it is up to the consumer to know
            // what data is on what topic.
            kafkaProducer = new KafkaProducer<>(producerProperties, new StringSerializer(), new ByteArraySerializer());
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating KafkaProducer for {} - {}: {}",
                    kafkaConfigDoc.getName(),
                    kafkaConfigDoc.getUuid(),
                    e.getMessage()),
                    e);
        }
        return new KafkaProducerSupplier(kafkaProducer, this::returnSupplier, key, kafkaConfigDocRef);
    }

    public void returnSupplier(final KafkaProducerSupplier kafkaProducerSupplier) {
        if (kafkaProducerSupplier != null && kafkaProducerSupplier.hasKafkaProducer()) {
            kafkaProducerSupplier.decrementUseCount();
            if (kafkaProducerSupplier.isSuperseded() && kafkaProducerSupplier.getUseCount() <= 0) {
                allProducerSuppliersMap.remove(kafkaProducerSupplier.getKafkaProducerSupplierKey());
                final KafkaProducer<String, byte[]> kafkaProducer = kafkaProducerSupplier.getKafkaProducer().get();
                kafkaProducer.close();
            }
        }
    }

    void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");

        allProducerSuppliersMap.values()
                .parallelStream()
                .forEach(kafkaProducerSupplier -> {
                    LOGGER.info("Closing Kafka producer for {}", kafkaProducerSupplier.getKafkaProducerSupplierKey());
                    kafkaProducerSupplier.getKafkaProducer().ifPresent(KafkaProducer::close);
                });
    }

    public static Properties getProperties(KafkaConfigDoc doc) {
        Properties properties = new Properties();
        if (doc.getData() != null && !doc.getData().isEmpty()) {
            StringReader reader = new StringReader(doc.getData());
            try {
                properties.load(reader);
            } catch (IOException ex) {
                LOGGER.error("Unable to read kafka properties from {} - {}", doc.getName(), doc.getUuid(), ex);
            }
        }
        return properties;
    }

    @Override
    public HealthCheck.Result getHealth() {

        List<Map<String,Object>> producerInfo = new ArrayList<>();

        allProducerSuppliersMap.values().forEach(kafkaProducerSupplier -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", kafkaProducerSupplier.getConfigName());
            map.put("uuid", kafkaProducerSupplier.getConfigUuid());
            map.put("version", kafkaProducerSupplier.getConfigVersion());
            map.put("useCount", kafkaProducerSupplier.getUseCount());
            producerInfo.add(map);
        });

        return HealthCheck.Result.builder()
                .healthy()
                .withDetail("producers", producerInfo)
                .build();
    }
}
