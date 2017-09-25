package stroom.connectors;

import org.junit.Test;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.node.server.StroomPropertyService;
import stroom.node.server.StroomPropertyServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TestKafkaExternalLoader {
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

        final Consumer<Exception> exceptionConsumer = (Consumer<Exception>) mock(Consumer.class);

        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getProducer(exceptionConsumer);

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        stroomKafkaProducer.send(record, true, e -> fail(e.getLocalizedMessage()));

        verify(exceptionConsumer, times(0));
    }

    @Test
    public void testKafkaProduceMisconfigured() {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final ExternalLibService externalLibService = new ExternalLibService(".");

        final StroomPropertyService propertyService = new StroomPropertyServiceImpl();

        final StroomKafkaProducerFactoryService kafkaProducerFactoryService =
                new StroomKafkaProducerFactoryService(propertyService, externalLibService);

        final Consumer<Exception> exceptionConsumer = (Consumer<Exception>) mock(Consumer.class);
        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getProducer(exceptionConsumer);

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        stroomKafkaProducer.send(record, true, e -> fail(e.getLocalizedMessage()));

        verify(exceptionConsumer, times(1)).accept(any(Exception.class));
    }
}
