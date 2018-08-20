package stroom.connectors.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import stroom.docref.DocRef;
import stroom.kafka.api.StroomKafkaProducer;
import stroom.kafka.api.StroomKafkaProducerFactory;
import stroom.kafka.api.StroomKafkaProducerRecord;
import stroom.kafka.api.StroomKafkaRecordMetaData;
import stroom.kafka.server.KafkaConfigStore;
import stroom.kafka.shared.KafkaConfigDoc;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
        // TODO : @66 Fix this test
class TestKafkaExternalLoader {
    private static final String DEV_EXTERNAL_LIB_DIR = System.getenv("HOME") + "/.stroom/plugins";

    @Mock
    private KafkaConfigStore kafkaConfigStore;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);

        final KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc();
        final Properties properties = kafkaConfigDoc.getProperties();
        properties.put("default.connector.version", "0.10.0.1");
        properties.put("stroom.connectors.kafka.default.bootstrap.servers", "localhost:9092");

        Mockito.when(kafkaConfigStore.readDocument(Mockito.any())).thenReturn(kafkaConfigDoc);


//        kafkaConfigDoc.setProperties();
//
//        mockPropertyService.setProperty("stroom.connectors.kafka.default.connector.version", "0.10.0.1");
//        mockPropertyService.setProperty("stroom.connectors.kafka.default.bootstrap.servers", "localhost:9092");
//        mockPropertyService.setProperty("stroom.plugins.lib.dir", DEV_EXTERNAL_LIB_DIR);
    }

    @Test
    void testKafkaProduce_sync() {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final StroomKafkaProducerFactory kafkaProducerFactory = new StroomKafkaProducerFactoryImpl(kafkaConfigStore);
        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactory.createProducer(new DocRef()).get();

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        StroomKafkaRecordMetaData metaData = stroomKafkaProducer.sendSync(record);

        assertThat(metaData).isNotNull();
    }

    @Test
    void testKafkaProduce_aSync() throws ExecutionException, InterruptedException {
        final String KAFKA_TOPIC = "filteredStroom";
        final String KAFKA_RECORD_KEY = "0";
        final String TEST_MESSAGE = "The dynamic loading of kafka is working under integration test";

        final StroomKafkaProducerFactory kafkaProducerFactory = new StroomKafkaProducerFactoryImpl(kafkaConfigStore);
        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactory.createProducer(new DocRef()).get();

        final Consumer<Throwable> exceptionConsumer = (Consumer<Throwable>) Mockito.mock(Consumer.class);

        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(KAFKA_TOPIC)
                        .key(KAFKA_RECORD_KEY)
                        .value(TEST_MESSAGE.getBytes(StandardCharsets.UTF_8))
                        .build();

        CompletableFuture<StroomKafkaRecordMetaData> future = stroomKafkaProducer.sendAsync(record, exceptionConsumer);

        assertThat(future).isNotNull();

        StroomKafkaRecordMetaData metaData = future.get();
        assertThat(metaData).isNotNull();
        assertThat(future).isCompleted();

        Mockito.verify(exceptionConsumer, Mockito.times(0));
    }

    @Test
    void testKafkaProduceMisconfigured() {
        //Empty prop service, so not kafka props to use
        final StroomKafkaProducerFactory kafkaProducerFactory = new StroomKafkaProducerFactoryImpl(kafkaConfigStore);
        final StroomKafkaProducer stroomKafkaProducer = kafkaProducerFactory.createProducer(new DocRef()).get();

        //no props so won't get a producer back
        assertThat(stroomKafkaProducer).isNull();
    }
}
