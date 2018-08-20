package stroom.connectors.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import stroom.docref.DocRef;
import stroom.kafka.api.StroomKafkaProducer;
import stroom.kafka.api.StroomKafkaProducerRecord;
import stroom.kafka.server.KafkaConfigStore;
import stroom.kafka.shared.KafkaConfigDoc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomKafkaProducer {
    @Mock
    private KafkaConfigStore kafkaConfigStore;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);

        final KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc();
        final Properties properties = kafkaConfigDoc.getProperties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");

        Mockito.when(kafkaConfigStore.readDocument(Mockito.any())).thenReturn(kafkaConfigDoc);


//        kafkaConfigDoc.setProperties();
//
//        mockPropertyService.setProperty("stroom.connectors.kafka.default.connector.version", "0.10.0.1");
//        mockPropertyService.setProperty("stroom.connectors.kafka.default.bootstrap.servers", "localhost:9092");
//        mockPropertyService.setProperty("stroom.plugins.lib.dir", DEV_EXTERNAL_LIB_DIR);
    }

    @Test
    @Disabled("You may use this to test the local instance of Kafka.")
    void testManualSend() {
        // Given
        final StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl(kafkaConfigStore);
        final StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.createProducer(new DocRef()).get();
        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data".getBytes(StandardCharsets.UTF_8))
                        .build();

        // When
        stroomKafkaProducer.sendSync(Collections.singletonList(record));

        // Then: manually check your Kafka instances 'statistics' topic for 'some record data'
    }

    @Test
    void testBadlyConfigured() {
        // Given
        final StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl(kafkaConfigStore);
        final StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.createProducer(new DocRef()).get();
        final StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data".getBytes(StandardCharsets.UTF_8))
                        .build();

        AtomicBoolean hasSendFailed = new AtomicBoolean(false);

        // When
        stroomKafkaProducer.sendSync(Collections.singletonList(record));

        // Then
        assertThat(hasSendFailed.get()).isTrue();
    }
}
