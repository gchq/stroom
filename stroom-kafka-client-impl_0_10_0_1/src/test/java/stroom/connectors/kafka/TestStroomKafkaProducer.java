package stroom.connectors.kafka;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.ConnectorPropertiesEmptyImpl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomKafkaProducer {
    private static final String KAFKA_VERSION = "0.10.0.1";

    public static final Consumer<Exception> DEFAULT_CALLBACK = ex -> {
        throw new RuntimeException(String.format("Exception during send"), ex);
    };

    @Test
//    @Ignore("You may use this to test the local instance of Kafka.")
    public void testManualSend() {
        // Given
        StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl();
        ConnectorProperties kafkaProps = new ConnectorPropertiesEmptyImpl();
        kafkaProps.put(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.createConnector(KAFKA_VERSION, kafkaProps);
        StroomKafkaProducerRecord<String, byte[]> record =
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
    public void testBadlyConfigured() {
        // Given
        StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl();
        ConnectorProperties properties = new ConnectorPropertiesEmptyImpl();
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.createConnector(KAFKA_VERSION, properties);
        StroomKafkaProducerRecord<String, byte[]> record =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data".getBytes(StandardCharsets.UTF_8))
                        .build();

        AtomicBoolean hasSendFailed = new AtomicBoolean(false);

        // When
        stroomKafkaProducer.sendSync(Collections.singletonList(record));

        // Then
        Assert.assertTrue(hasSendFailed.get());
    }
}
