package stroom.connectors.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.kafka.api.StroomKafkaProducer;
import stroom.kafka.api.StroomKafkaProducerFactory;
import stroom.kafka.server.KafkaConfigStore;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.lifecycle.StroomShutdown;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class StroomKafkaProducerFactoryImpl implements StroomKafkaProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryImpl.class);

    private static final String VERSION = "0.10.0.1";

    private final KafkaConfigStore kafkaConfigStore;

    @Inject
    public StroomKafkaProducerFactoryImpl(final KafkaConfigStore kafkaConfigStore) {
        this.kafkaConfigStore = kafkaConfigStore;
    }

    @Override
    public Optional<StroomKafkaProducer> createProducer(final DocRef kafkaConfigRef) {
        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigRef);
        if (VERSION.equals(kafkaConfigDoc.getKafkaVersion())) {
            return Optional.of(new StroomKafkaProducerImpl(kafkaConfigDoc));
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
