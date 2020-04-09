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
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.KafkaProducerSupplier;
import stroom.kafka.api.KafkaProducerSupplierKey;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

    private final KafkaConfigDocCache kafkaConfigDocCache;

    // Keyed on uuid, represents the current KP for each UUID. These are the KPs that are given out to
    // new calls to getSupplier()
    private final ConcurrentMap<String, KafkaProducerSupplierImpl> currentProducerSuppliersMap = new ConcurrentHashMap<>();
    // Keyed on uuid|version so can hold one or more KPs for each uuid if the config has changed over
    // time. Once clients are no longer using the superseded KPs they will be removed.
    private final ConcurrentMap<KafkaProducerSupplierKey, KafkaProducerSupplierImpl> allProducerSuppliersMap = new ConcurrentHashMap<>();

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigDocCache kafkaConfigDocCache) {
        this.kafkaConfigDocCache = kafkaConfigDocCache;
    }

    @Override
    public KafkaProducerSupplier getSupplier(final DocRef kafkaConfigDocRef) {
        Objects.requireNonNull(kafkaConfigDocRef);
        Objects.requireNonNull(kafkaConfigDocRef.getUuid(),
                "No Kafka config UUID has been defined");

        final KafkaProducerSupplierImpl kafkaProducerSupplier;
        final Optional<KafkaConfigDoc> optKafkaConfigDoc = kafkaConfigDocCache.get(kafkaConfigDocRef);

        if (optKafkaConfigDoc.isPresent()) {
            KafkaConfigDoc kafkaConfigDoc = optKafkaConfigDoc.get();
            final KafkaProducerSupplierKey desiredKey = new KafkaProducerSupplierKey(kafkaConfigDoc);

            // Optimistically assume the map will have the latest KafkaProducerSupplier
            final KafkaProducerSupplierImpl activeKafkaProducerSupplier = currentProducerSuppliersMap.get(desiredKey.getUuid());
            if (activeKafkaProducerSupplier != null
                    && desiredKey.equals(activeKafkaProducerSupplier.getKafkaProducerSupplierKey())) {
                // This is the latest KafkaProducerSupplier so use it
                kafkaProducerSupplier = activeKafkaProducerSupplier;
            } else {
                // Not there or not latest so compute a new/updated one atomically
                kafkaProducerSupplier = currentProducerSuppliersMap.compute(
                        kafkaConfigDoc.getUuid(),
                        (k, existingValue) -> {
                            final KafkaProducerSupplierImpl producerSupplier;
                            if (existingValue == null) {
                                // Don't have one so create a new one
                                producerSupplier = createProducerSupplier(kafkaConfigDoc, kafkaConfigDocRef, desiredKey);
                            } else {
                                // we already have one so check if it should be current
                                if (desiredKey.equals(existingValue.getKafkaProducerSupplierKey())) {
                                    // is up to date
                                    producerSupplier = existingValue;
                                } else {
                                    // needs to be superseded
                                    existingValue.markSuperseded();

                                    // swap out the existing current supplier for our new one
                                    producerSupplier = createProducerSupplier(kafkaConfigDoc, kafkaConfigDocRef, desiredKey);
                                }
                            }
                            return producerSupplier;
                        });
            }
            // Increment the use count so we can track when objects are unused and can be closed.
            kafkaProducerSupplier.incrementUseCount();
        } else {
            // No doc for this docref so return an empty supplier
            kafkaProducerSupplier = KafkaProducerSupplierImpl.empty();
        }
        return kafkaProducerSupplier;
    }

    @Override
    public void returnSupplier(final KafkaProducerSupplier kafkaProducerSupplier) {

        if (kafkaProducerSupplier != null && kafkaProducerSupplier.hasKafkaProducer()) {
            if (kafkaProducerSupplier instanceof KafkaProducerSupplierImpl) {
                KafkaProducerSupplierImpl kafkaProducerSupplierImpl = (KafkaProducerSupplierImpl) kafkaProducerSupplier;
                kafkaProducerSupplierImpl.decrementUseCount();
                if (kafkaProducerSupplierImpl.isSuperseded() && kafkaProducerSupplierImpl.getUseCount() <= 0) {
                    allProducerSuppliersMap.remove(kafkaProducerSupplierImpl.getKafkaProducerSupplierKey());
                    final KafkaProducer<String, byte[]> kafkaProducer = kafkaProducerSupplier.getKafkaProducer().get();
                    kafkaProducer.close();
                }
            } else {
                throw new RuntimeException("Unexpected type " + kafkaProducerSupplier.getClass().getName());
            }
        }
    }

    private KafkaProducerSupplierImpl createProducerSupplier(
            final KafkaConfigDoc kafkaConfigDoc,
            final DocRef kafkaConfigDocRef,
            final KafkaProducerSupplierKey key) {

        final Properties producerProperties = getProperties(kafkaConfigDoc);
        final KafkaProducer<String, byte[]> kafkaProducer;
        try {
            // For flexibility we always use a byte[] as the msg value.  This means the same producer
            // can cope with different flavours of data and it is up to the consumer to know
            // what data is on what topic. It is also up to the use of the KP to serialise down to a byte[]
            kafkaProducer = new KafkaProducer<>(producerProperties, new StringSerializer(), new ByteArraySerializer());
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating KafkaProducer for {} - {}: {}",
                    kafkaConfigDoc.getName(),
                    kafkaConfigDoc.getUuid(),
                    e.getMessage()),
                    e);
        }
        final KafkaProducerSupplierImpl kafkaProducerSupplier = new KafkaProducerSupplierImpl(
                kafkaProducer, this::returnSupplier, key, kafkaConfigDocRef);
        // Hold on to a reference to each one we create
        allProducerSuppliersMap.put(key, kafkaProducerSupplier);
        return kafkaProducerSupplier;
    }


    void shutdown() {
        LOGGER.info("Shutting down Stroom Kafka Producer Factory Service");

        allProducerSuppliersMap.values()
                .parallelStream()
                .forEach(kafkaProducerSupplier -> {
                    kafkaProducerSupplier.getKafkaProducer().ifPresent(kafkaProducer -> {
                        LOGGER.info("Closing Kafka producer for {}", kafkaProducerSupplier.getKafkaProducerSupplierKey());
                        kafkaProducer.close();
                    });
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

        final List<Map<String,Object>> producerInfo = allProducerSuppliersMap.values().stream()
                .map(kafkaProducerSupplier -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("docName", kafkaProducerSupplier.getConfigName());
                    map.put("docUuid", kafkaProducerSupplier.getConfigUuid());
                    map.put("docVersion", kafkaProducerSupplier.getConfigVersion());
                    map.put("useCount", kafkaProducerSupplier.getUseCount());
                    return map;
                })
                .collect(Collectors.toList());

        return HealthCheck.Result.builder()
                .healthy()
                .withDetail("producers", producerInfo)
                .build();
    }
}
