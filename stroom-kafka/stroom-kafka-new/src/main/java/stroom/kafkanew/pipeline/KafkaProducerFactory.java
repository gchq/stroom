package stroom.kafkanew.pipeline;

import stroom.docref.DocRef;
import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.Optional;

public interface KafkaProducerFactory {
    Optional<KafkaProducer<String,String>> createProducer(DocRef kafkaConfigRef);
}
