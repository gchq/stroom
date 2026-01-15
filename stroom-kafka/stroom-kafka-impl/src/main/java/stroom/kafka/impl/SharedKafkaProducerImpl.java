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
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.api.SharedKafkaProducerIdentity;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SharedKafkaProducerImpl implements SharedKafkaProducer {

    private final KafkaProducer<String, byte[]> kafkaProducer;
    private final Consumer<SharedKafkaProducer> closeAction;
    private final SharedKafkaProducerIdentity sharedKafkaProducerIdentity;
    private final DocRef kafkaConfigRef;
    private final AtomicBoolean isSuperseded = new AtomicBoolean(false);
    private final AtomicInteger useCounter = new AtomicInteger(0);
    private final Instant createdTime;
    private volatile Instant lastAccessedTime;

    SharedKafkaProducerImpl(final KafkaProducer<String, byte[]> kafkaProducer,
                            final Consumer<SharedKafkaProducer> closeAction,
                            final SharedKafkaProducerIdentity sharedKafkaProducerIdentity,
                            final DocRef kafkaConfigRef) {
        this.kafkaProducer = kafkaProducer;
        this.closeAction = closeAction;
        this.sharedKafkaProducerIdentity = sharedKafkaProducerIdentity;
        this.kafkaConfigRef = kafkaConfigRef;
        this.createdTime = Instant.now();
        this.lastAccessedTime = createdTime;
    }

    @Override
    public Optional<KafkaProducer<String, byte[]>> getKafkaProducer() {
        lastAccessedTime = Instant.now();
        return Optional.ofNullable(kafkaProducer);
    }

    @Override
    public boolean hasKafkaProducer() {
        return kafkaProducer != null;
    }

    @Override
    public String getConfigName() {
        return kafkaConfigRef != null
                ? kafkaConfigRef.getName()
                : null;
    }

    @Override
    public String getConfigUuid() {
        return sharedKafkaProducerIdentity != null
                ? sharedKafkaProducerIdentity.getConfigUuid()
                : null;
    }

    @Override
    public String getConfigVersion() {
        return sharedKafkaProducerIdentity != null
                ? sharedKafkaProducerIdentity.getConfigVersion()
                : null;
    }

    /**
     * May close the wrapped {@link KafkaProducer} if it is deemed to no longer be needed.
     */
    @Override
    public void close() {
        closeAction.accept(this);
    }

    static SharedKafkaProducerImpl empty() {
        return new SharedKafkaProducerImpl(null,
                kafkaProducerSupplier -> {
                },
                null,
                null);
    }

    SharedKafkaProducerIdentity getSharedKafkaProducerIdentity() {
        return sharedKafkaProducerIdentity;
    }

    boolean isSuperseded() {
        return isSuperseded.get();
    }

    void markSuperseded() {
        isSuperseded.set(true);
    }

    void incrementUseCount() {
        useCounter.incrementAndGet();
    }

    void decrementUseCount() {
        useCounter.decrementAndGet();
    }

    int getUseCount() {
        return useCounter.get();
    }

    Instant getCreatedTime() {
        return createdTime;
    }

    Instant getLastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public String toString() {
        return "SharedKafkaProducerImpl{" +
                "sharedKafkaProducerIdentity=" + sharedKafkaProducerIdentity +
                ", isSuperseded=" + isSuperseded +
                ", useCounter=" + useCounter +
                '}';
    }
}
