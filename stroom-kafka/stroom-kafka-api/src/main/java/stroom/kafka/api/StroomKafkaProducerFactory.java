package stroom.kafka.api;

import stroom.docref.DocRef;

import java.util.Optional;

public interface StroomKafkaProducerFactory {
    Optional<StroomKafkaProducer> createProducer(DocRef kafkaConfigRef);
}
