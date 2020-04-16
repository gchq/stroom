package stroom.kafka.api;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.Optional;

/**
 * A wrapper for a shared {@link KafkaProducer} that MUST be used in a try-with-resources
 * block (or similar mechanism to call {@link SharedKafkaProducer#close()}) so that
 * the {@link KafkaProducer} can be closed when no longer needed by all parties.
 * An instance may not contain a {@link KafkaProducer}, e.g. when no {@link stroom.kafka.shared.KafkaConfigDoc}
 * can be found for a UUID.
 *
 * Users of this class should NOT call close() on the KafkaProducer themselves as it is potentially shared.
 * They are permitted to flush it though.
 */
public interface SharedKafkaProducer extends AutoCloseable {

    Optional<KafkaProducer<String, byte[]>> getKafkaProducer();

    boolean hasKafkaProducer();

    String getConfigName();

    String getConfigUuid();

    String getConfigVersion();

    /**
     * This does not close the contained {@link KafkaProducer}, it just marks it
     * as being no longer in use by the caller. The {@link KafkaProducerFactory}
     * is responsible for closing the {@link KafkaProducer}
     */
    @Override
    void close();
}
