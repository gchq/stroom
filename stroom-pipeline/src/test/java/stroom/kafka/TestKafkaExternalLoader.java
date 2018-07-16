package stroom.kafka;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import stroom.connectors.ExternalLibService;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.connectors.kafka.StroomKafkaRecordMetaData;
import stroom.properties.impl.mock.MockPropertyService;
import stroom.properties.api.PropertyService;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class TestKafkaExternalLoader {
    private static final String DEV_EXTERNAL_LIB_DIR = System.getenv("HOME") + "/.stroom/plugins";

    private final MockPropertyService mockPropertyService = new MockPropertyService();

    @Before
    public void setup() {
        mockPropertyService.setProperty("stroom.connectors.kafka.default.connector.version", "0.10.0.1");
        mockPropertyService.setProperty("stroom.connectors.kafka.default.bootstrap.servers", "localhost:9092");
        mockPropertyService.setProperty("stroom.plugins.lib.dir", DEV_EXTERNAL_LIB_DIR);
    }

    @Test
    public void testKafkaProduce_sync() {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final ExternalLibService externalLibService = new ExternalLibService(mockPropertyService);

        final StroomKafkaProducerFactoryService kafkaProducerFactoryService =
                new StroomKafkaProducerFactoryService(mockPropertyService, externalLibService);


        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getConnector().get();

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        StroomKafkaRecordMetaData metaData = stroomKafkaProducer.sendSync(record);

        Assertions.assertThat(metaData).isNotNull();
    }

    @Test
    public void testKafkaProduce_aSync() throws ExecutionException, InterruptedException {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final ExternalLibService externalLibService = new ExternalLibService(mockPropertyService);

        final StroomKafkaProducerFactoryService kafkaProducerFactoryService =
                new StroomKafkaProducerFactoryService(mockPropertyService, externalLibService);

        final Consumer<Throwable> exceptionConsumer = (Consumer<Throwable>) Mockito.mock(Consumer.class);

        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactoryService.getConnector().get();

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        CompletableFuture<StroomKafkaRecordMetaData> future = stroomKafkaProducer.sendAsync(record, exceptionConsumer);

        Assertions.assertThat(future).isNotNull();

        StroomKafkaRecordMetaData metaData = future.get();
        Assertions.assertThat(metaData).isNotNull();
        Assertions.assertThat(future).isCompleted();

        Mockito.verify(exceptionConsumer, Mockito.times(0));
    }

    @Test
    public void testKafkaProduceMisconfigured() {

        //Empty prop service, so not kafka props to use
        final PropertyService emptyPropertyService = new MockPropertyService();

        final ExternalLibService externalLibService = new ExternalLibService(emptyPropertyService);


        final StroomKafkaProducerFactoryService kafkaProducerFactoryService =
                new StroomKafkaProducerFactoryService(emptyPropertyService, externalLibService);

        final Consumer<Exception> exceptionConsumer = (Consumer<Exception>) Mockito.mock(Consumer.class);
        final Optional<StroomKafkaProducer> stroomKafkaProducer = kafkaProducerFactoryService.getConnector();

        //no props so won't get a producer back
        Assertions.assertThat(stroomKafkaProducer).isEmpty();
    }
}
