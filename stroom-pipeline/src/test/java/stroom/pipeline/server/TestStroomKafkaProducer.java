package stroom.pipeline.server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.ConnectorPropertiesPrefixImpl;
import stroom.connectors.kafka.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomKafkaProducer {
    private static final String KAFKA_VERSION = "0.10.0.1";

    public static final Consumer<Exception> DEFAULT_CALLBACK = ex -> {
        throw new RuntimeException(String.format("Exception during send"), ex);
    };

    @Test
    @Ignore("You may use this to test the local instance of Kafka.")
    public void testManualSend() {
        // Given
        StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl();
        ConnectorProperties kafkaProps = new ConnectorPropertiesPrefixImpl();
        kafkaProps.put(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.getProducer(KAFKA_VERSION, kafkaProps);
        StroomKafkaProducerRecord<String, String> record =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data")
                        .build();

        // When
        stroomKafkaProducer.send(record, true, DEFAULT_CALLBACK);

        // Then: manually check your Kafka instances 'statistics' topic for 'some record data'
    }

    @Test
    public void testBadlyConfigured() {
        // Given
        StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl();
        ConnectorProperties properties = new ConnectorPropertiesPrefixImpl(null, null);
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.getProducer(KAFKA_VERSION, properties);
        StroomKafkaProducerRecord<String, String> record =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data")
                        .build();

        AtomicBoolean hasSendFailed = new AtomicBoolean(false);

        // When
        stroomKafkaProducer.send(record, true, ex -> hasSendFailed.set(true));

        // Then
        Assert.assertTrue(hasSendFailed.get());
    }
}
