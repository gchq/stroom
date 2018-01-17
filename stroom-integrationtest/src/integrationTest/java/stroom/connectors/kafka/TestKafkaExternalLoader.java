package stroom.connectors.kafka;

import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import stroom.connectors.ExternalLibService;
import stroom.node.server.StroomPropertyService;
import stroom.node.server.StroomPropertyServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.fail;

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

        final Consumer<Exception> exceptionConsumer = (Consumer<Exception>) Mockito.mock(Consumer.class);

        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getConnector().get();

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        stroomKafkaProducer.sendSync(Collections.singletonList(record));

        Mockito.verify(exceptionConsumer, Mockito.times(0));
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

        final Consumer<Exception> exceptionConsumer = (Consumer<Exception>) Mockito.mock(Consumer.class);
        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getConnector().get();

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        stroomKafkaProducer.sendAsync(record, true, e -> fail(e.getLocalizedMessage()));

        Mockito.verify(exceptionConsumer, Mockito.times(1)).accept(Matchers.any(Exception.class));
    }
}
