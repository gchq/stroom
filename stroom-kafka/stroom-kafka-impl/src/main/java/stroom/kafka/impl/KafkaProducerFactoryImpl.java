package stroom.kafka.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaProducer;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.lifecycle.StroomShutdown;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class KafkaProducerFactoryImpl implements KafkaProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerFactoryImpl.class);

    private static final String VERSION = "0.10.0.1";

    private final KafkaConfigStore kafkaConfigStore;

    @Inject
    KafkaProducerFactoryImpl(final KafkaConfigStore kafkaConfigStore) {
        this.kafkaConfigStore = kafkaConfigStore;
    }

    @Override
    public Optional<KafkaProducer> createProducer(final DocRef kafkaConfigRef) {
        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigRef);
        if (VERSION.equals(kafkaConfigDoc.getKafkaVersion())) {
            return Optional.of(new KafkaProducerImpl(kafkaConfigDoc));
        } else {
            LOGGER.debug("Requested version [{}] doesn't match my version [{}]", kafkaConfigDoc.getKafkaVersion(), VERSION);
        }
        return Optional.empty();
    }


    @StroomShutdown
    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
//        super.shutdown();
    }
}
