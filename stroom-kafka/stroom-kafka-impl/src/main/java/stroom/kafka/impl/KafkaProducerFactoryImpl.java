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

package stroom.kafka.impl;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.api.SharedKafkaProducerIdentity;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

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
 * Class to provide shared {@link KafkaProducer} instances wrapped inside a {@link SharedKafkaProducer}.
 * We want one {@link KafkaProducer} per {@link KafkaConfigDoc}, however the config in a {@link KafkaConfigDoc}
 * can change at runtime so on each request for a {@link SharedKafkaProducer} we need to check if the
 * {@link KafkaConfigDoc} has changed since the time we created it. New calls to
 * {@link KafkaProducerFactory#getSharedProducer(DocRef)} will always get a {@link SharedKafkaProducer} for the
 * latest config. {@link KafkaProducer}
 */
@Singleton
class KafkaProducerFactoryImpl implements KafkaProducerFactory, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KafkaProducerFactoryImpl.class);

    private final KafkaConfigDocCache kafkaConfigDocCache;

    // Keyed on uuid, represents the current KP for each UUID. These are the KPs that are given out to
    // new calls to getSharedProducer()
    private final ConcurrentMap<String, SharedKafkaProducerImpl> currentSharedProducersMap = new ConcurrentHashMap<>();
    // Keyed on uuid|version so can hold one or more KPs for each uuid if the config has changed over
    // time. Once clients are no longer using the superseded KPs they will be removed.
    private final ConcurrentMap<SharedKafkaProducerIdentity, SharedKafkaProducerImpl> allSharedProducersMap =
            new ConcurrentHashMap<>();

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigDocCache kafkaConfigDocCache) {
        this.kafkaConfigDocCache = kafkaConfigDocCache;
    }

    @Override
    public SharedKafkaProducer getSharedProducer(final DocRef kafkaConfigDocRef) {
        Objects.requireNonNull(kafkaConfigDocRef);
        Objects.requireNonNull(kafkaConfigDocRef.getUuid(),
                "No Kafka config UUID has been defined");

        final SharedKafkaProducerImpl sharedKafkaProducer;
        final Optional<KafkaConfigDoc> optKafkaConfigDoc = kafkaConfigDocCache.get(kafkaConfigDocRef);

        if (optKafkaConfigDoc.isPresent()) {
            final KafkaConfigDoc kafkaConfigDoc = optKafkaConfigDoc.get();
            final DocRef docRefFromDoc = DocRefUtil.create(kafkaConfigDoc);
            final SharedKafkaProducerIdentity desiredKey = new SharedKafkaProducerIdentity(kafkaConfigDoc);

            // Optimistically assume the map will have the latest SharedKafkaProducer
            final SharedKafkaProducerImpl activeSharedProducer = currentSharedProducersMap.get(
                    desiredKey.getConfigUuid());
            if (activeSharedProducer != null
                    && desiredKey.equals(activeSharedProducer.getSharedKafkaProducerIdentity())) {
                // This is the latest SharedKafkaProducer so use it
                LOGGER.debug("Using latest SharedKafkaProducer");
                sharedKafkaProducer = activeSharedProducer;
            } else {
                // Not there or not latest so compute a new/updated one atomically
                sharedKafkaProducer = currentSharedProducersMap.compute(
                        kafkaConfigDoc.getUuid(),
                        (k, existingValue) -> {
                            final SharedKafkaProducerImpl sharedKafkaProducer2;
                            if (existingValue == null) {
                                // Don't have one so create a new one
                                sharedKafkaProducer2 = createSharedProducer(kafkaConfigDoc, docRefFromDoc, desiredKey);
                            } else {
                                // we already have one so check if it should be current
                                if (desiredKey.equals(existingValue.getSharedKafkaProducerIdentity())) {
                                    // is up to date
                                    LOGGER.debug("Existing SharedKafkaProducer is up to date");
                                    sharedKafkaProducer2 = existingValue;
                                } else {
                                    // needs to be superseded
                                    existingValue.markSuperseded();
                                    LOGGER.debug("Superseding existing sharedKafkaProducer {}", existingValue);

                                    // If nobody is using the superseded one then we can bin it now
                                    removeRedundantSharedKafkaProducers();

                                    // swap out the existing current SharedKafkaProducer for our new one
                                    sharedKafkaProducer2 = createSharedProducer(kafkaConfigDoc,
                                            docRefFromDoc,
                                            desiredKey);
                                }
                            }
                            return sharedKafkaProducer2;
                        });
            }
            // Increment the use count so we can track when objects are unused and can be closed.
            sharedKafkaProducer.incrementUseCount();
        } else {
            // No doc for this docref so return an empty SharedKafkaProducer
            sharedKafkaProducer = SharedKafkaProducerImpl.empty();
        }
        return sharedKafkaProducer;
    }

    private void removeRedundantSharedKafkaProducers() {
        final boolean didRemoveElements = allSharedProducersMap.values().removeIf(sharedKafkaProducer ->
                sharedKafkaProducer.isSuperseded() && sharedKafkaProducer.getUseCount() <= 0);
        if (LOGGER.isDebugEnabled()) {
            if (didRemoveElements) {
                // If removeRedundantSharedKafkaProducers is called before we add the new one then the size
                // may be zero
                LOGGER.debug("Removed items from allSharedProducersMap, new size {}",
                        allSharedProducersMap.size());
            }
        }
    }

    @Override
    public void returnSharedKafkaProducer(final SharedKafkaProducer sharedKafkaProducer) {

        if (sharedKafkaProducer != null && sharedKafkaProducer.hasKafkaProducer()) {
            sharedKafkaProducer.close();
        }
    }

    private void returnAction(final SharedKafkaProducer sharedKafkaProducer) {

        LOGGER.debug("returnAction called for {}", sharedKafkaProducer);
        if (sharedKafkaProducer != null && sharedKafkaProducer.hasKafkaProducer()) {
            if (sharedKafkaProducer instanceof SharedKafkaProducerImpl) {
                final SharedKafkaProducerImpl sharedKafkaProducerImpl = (SharedKafkaProducerImpl) sharedKafkaProducer;
                sharedKafkaProducerImpl.decrementUseCount();
                LOGGER.debug("superseded is {} and useCount is {} after decrement",
                        sharedKafkaProducerImpl.isSuperseded(), sharedKafkaProducerImpl.getUseCount());
                if (sharedKafkaProducerImpl.isSuperseded() && sharedKafkaProducerImpl.getUseCount() <= 0) {
                    allSharedProducersMap.remove(sharedKafkaProducerImpl.getSharedKafkaProducerIdentity());
                    final KafkaProducer<String, byte[]> kafkaProducer = sharedKafkaProducer.getKafkaProducer().get();
                    kafkaProducer.close();
                }
            } else {
                throw new RuntimeException("Unexpected type " + sharedKafkaProducer.getClass().getName());
            }
        }
    }

    private SharedKafkaProducerImpl createSharedProducer(
            final KafkaConfigDoc kafkaConfigDoc,
            final DocRef kafkaConfigDocRef,
            final SharedKafkaProducerIdentity key) {

        final Properties producerProperties = getProperties(kafkaConfigDoc);
        final KafkaProducer<String, byte[]> kafkaProducer;
        try {
            // For flexibility we always use a byte[] as the msg value.  This means the same producer
            // can cope with different flavours of data and it is up to the consumer to know
            // what data is on what topic. It is also up to the use of the KP to serialise down to a byte[]
            LOGGER.debug("Creating KafkaProducer for {}", key);
            kafkaProducer = new KafkaProducer<>(producerProperties, new StringSerializer(), new ByteArraySerializer());
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating KafkaProducer for {} - {}: {}",
                    kafkaConfigDoc.getName(),
                    kafkaConfigDoc.getUuid(),
                    e.getMessage()),
                    e);
        }
        final SharedKafkaProducerImpl sharedKafkaProducer = new SharedKafkaProducerImpl(
                kafkaProducer, this::returnAction, key, kafkaConfigDocRef);
        // Hold on to a reference to each one we create
        allSharedProducersMap.put(key, sharedKafkaProducer);
        return sharedKafkaProducer;
    }


    void shutdown() {
        LOGGER.info("Shutting down Stroom Kafka Producer Factory Service");

        allSharedProducersMap.values()
                .parallelStream()
                .forEach(sharedKafkaProducer -> {
                    sharedKafkaProducer.getKafkaProducer().ifPresent(kafkaProducer -> {
                        LOGGER.info("Closing Kafka producer for {}",
                                sharedKafkaProducer.getSharedKafkaProducerIdentity());
                        kafkaProducer.close();
                    });
                });
    }

    public static Properties getProperties(final KafkaConfigDoc doc) {
        final Properties properties = new Properties();
        if (doc.getData() != null && !doc.getData().isEmpty()) {
            final StringReader reader = new StringReader(doc.getData());
            try {
                properties.load(reader);
            } catch (final IOException ex) {
                LOGGER.error("Unable to read kafka properties from {} - {}", doc.getName(), doc.getUuid(), ex);
            }
        }
        return properties;
    }

    @Override
    public SystemInfoResult getSystemInfo() {

        final List<Map<String, Object>> producerInfo = allSharedProducersMap.values().stream()
                .map(sharedKafkaProducer -> {
                    final Map<String, Object> map = new HashMap<>();
                    map.put("docName", sharedKafkaProducer.getConfigName());
                    map.put("docUuid", sharedKafkaProducer.getConfigUuid());
                    map.put("docVersion", sharedKafkaProducer.getConfigVersion());
                    map.put("useCount", sharedKafkaProducer.getUseCount());
                    map.put("isSuperseded", sharedKafkaProducer.isSuperseded());
                    map.put("createdTime", sharedKafkaProducer.getCreatedTime().toString());
                    map.put("lastAccessedTime", sharedKafkaProducer.getLastAccessedTime().toString());

                    sharedKafkaProducer.getKafkaProducer().ifPresent(kafkaProducer -> {
                        final Map<String, Map<String, Object>> metrics = kafkaProducer.metrics()
                                .entrySet()
                                .stream()
                                .map(entry -> {
                                    final String groupName = entry.getKey().group()
                                            + " ("
                                            + entry.getKey().tags().entrySet()
                                            .stream()
                                            .filter(entry2 -> !entry2.getKey().equals("client-id"))
                                            .map(entry2 -> entry2.getKey() + "=" + entry2.getValue())
                                            .collect(Collectors.joining(","))
                                            + ")";

                                    return Tuple.of(
                                            groupName,
                                            entry.getKey().name(),
                                            entry.getValue().metricValue());
                                })
                                .collect(Collectors.groupingBy(Tuple3::_1, Collectors.toMap(
                                        Tuple3::_2,
                                        Tuple3::_3)));
                        map.put("kafkaProducerMetrics", metrics);
                    });
                    return map;
                })
                .toList();

        return SystemInfoResult.builder(this)
                .addDetail("sharedProducers", producerInfo)
                .build();
    }
}
