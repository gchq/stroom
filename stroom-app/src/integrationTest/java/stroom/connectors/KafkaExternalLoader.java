package stroom.connectors;

import org.junit.Test;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.node.server.StroomPropertyService;
import stroom.node.server.StroomPropertyServiceImpl;

import static org.junit.Assert.fail;

public class KafkaExternalLoader {
    private static final String DEV_EXTERNAL_LIB_DIR = System.getenv("HOME") + "/.stroom/plugins";


    @Test
    public void testKafkaProduce() {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final ExternalLibService externalLibService = new ExternalLibService(DEV_EXTERNAL_LIB_DIR);

        final StroomPropertyService propertyService = new StroomPropertyServiceImpl();

        final StroomKafkaProducerFactoryService kafkaProducerFactoryService =
                new StroomKafkaProducerFactoryService(propertyService, externalLibService);

        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getProducer();

        final StroomKafkaProducerRecord<String, String> record =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE)
                        .build();

        stroomKafkaProducer.send(record, true, e -> fail(e.getLocalizedMessage()));
    }
}
