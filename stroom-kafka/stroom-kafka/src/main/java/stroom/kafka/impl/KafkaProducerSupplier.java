package stroom.kafka.impl;

import org.apache.kafka.clients.producer.KafkaProducer;
import stroom.docref.DocRef;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A wrapper for a shared {@link KafkaProducer} that MUST be used in a try-with-resources
 * block (or similar mechanism to call {@link KafkaProducerSupplier#close()}) so that
 * the {@link KafkaProducer} is closed when no longer needed by all parties.
 * An instance may not contain a {@link KafkaProducer}, e.g. when no {@link stroom.kafkaConfig.shared.KafkaConfigDoc}
 * can be found for a UUID.
 *
 * Users of this class should NOT call close() on the KafkaProducer themselves as it is potentially shared.
 * They are permitted to flush it though.
 */
public class KafkaProducerSupplier implements AutoCloseable {

    private final KafkaProducer<String, byte[]> kafkaProducer;
    private final Consumer<KafkaProducerSupplier> closeAction;
    private final KafkaProducerSupplierKey kafkaProducerSupplierKey;
    private final DocRef kafkaConfigRef;
    private final AtomicBoolean isSuperseded = new AtomicBoolean(false);
    private final AtomicInteger useCounter = new AtomicInteger(0);

    KafkaProducerSupplier(final KafkaProducer<String, byte[]> kafkaProducer,
                          final Consumer<KafkaProducerSupplier> closeAction,
                          final KafkaProducerSupplierKey kafkaProducerSupplierKey,
                          final DocRef kafkaConfigRef) {
        this.kafkaProducer = kafkaProducer;
        this.closeAction = closeAction;
        this.kafkaProducerSupplierKey = kafkaProducerSupplierKey;
        this.kafkaConfigRef = kafkaConfigRef;
    }

    public Optional<KafkaProducer<String, byte[]>> getKafkaProducer() {
        return Optional.ofNullable(kafkaProducer);
    }

    public boolean hasKafkaProducer() {
        return kafkaProducer != null;
    }

    public String getConfigName() {
        return kafkaConfigRef != null ? kafkaConfigRef.getName() : null;
    }

    public String getConfigUuid() {
        return kafkaProducerSupplierKey != null ? kafkaProducerSupplierKey.getUuid() : null;
    }

    public String getConfigVersion() {
        return kafkaProducerSupplierKey != null ? kafkaProducerSupplierKey.getVersion() : null;
    }

    /**
     * May close the wrapped {@link KafkaProducer} if it is deemed to no longer be needed.
     */
    @Override
    public void close() {
        closeAction.accept(this);
    }

    static KafkaProducerSupplier empty() {
        return new KafkaProducerSupplier(null,
                kafkaProducerSupplier -> {},
                null,
                null);
    }

    KafkaProducerSupplierKey getKafkaProducerSupplierKey() {
        return kafkaProducerSupplierKey;
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
}
