package stroom.kafka.api;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.Optional;

public interface KafkaProducerSupplier extends AutoCloseable {

    Optional<KafkaProducer<String, byte[]>> getKafkaProducer();

    boolean hasKafkaProducer();

    String getConfigName();

    String getConfigUuid();

    String getConfigVersion();

    @Override
    void close();
}
