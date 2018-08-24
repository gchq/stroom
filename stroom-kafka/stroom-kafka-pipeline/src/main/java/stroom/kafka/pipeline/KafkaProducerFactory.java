package stroom.kafka.pipeline;

import stroom.docref.DocRef;

import java.util.Optional;

public interface KafkaProducerFactory {
    Optional<KafkaProducer> createProducer(DocRef kafkaConfigRef);
}
