package stroom.pipeline.server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
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
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.getProducer(KAFKA_VERSION, "stroom.kafka:9092");
        StroomKafkaProducerRecord<String, String> record =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data")
                        .build();

        // When
        stroomKafkaProducer.send(record, FlushMode.FLUSH_ON_SEND, DEFAULT_CALLBACK);

        // Then: manually check your Kafka instances 'statistics' topic for 'some record data'
    }

    @Test
    public void testBadlyConfigured() {
        // Given
        StroomKafkaProducerFactoryImpl stroomKafkaProducerFactory = new StroomKafkaProducerFactoryImpl();
        StroomKafkaProducer stroomKafkaProducer = stroomKafkaProducerFactory.getProducer(KAFKA_VERSION, null);
        StroomKafkaProducerRecord<String, String> record =
                new StroomKafkaProducerRecord.Builder<String, String>()
                        .topic("statistics")
                        .key("statistics")
                        .value("some record data")
                        .build();

        AtomicBoolean hasSendFailed = new AtomicBoolean(false);

        // When
        stroomKafkaProducer.send(record, FlushMode.FLUSH_ON_SEND, ex -> hasSendFailed.set(true));

        // Then
        Assert.assertTrue(hasSendFailed.get());
    }
}
