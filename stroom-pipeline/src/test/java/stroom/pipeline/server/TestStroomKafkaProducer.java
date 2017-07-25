package stroom.pipeline.server;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.kafka.StroomKafkaProducer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomKafkaProducer {

    public static final Consumer<Exception> DEFAULT_CALLBACK = ex -> {
        throw new RuntimeException(String.format("Exception during send"), ex);
    };

    @Test
    @Ignore("You may use this to test the local instance of Kafka.")
    public void testManualSend() {
        // Given
        StroomKafkaProducer stroomKafkaProducer = new StroomKafkaProducer("stroom.kafka:9092");
        ProducerRecord<String, String> record = new ProducerRecord<>("statistics", "statistics", "some record data");

        // When
        stroomKafkaProducer.send(record, StroomKafkaProducer.FlushMode.FLUSH_ON_SEND, DEFAULT_CALLBACK);

        // Then: manually check your Kafka instances 'statistics' topic for 'some record data'
    }

    @Test
    public void testBadlyConfigured() {
        // Given
        StroomKafkaProducer stroomKafkaProducer = new StroomKafkaProducer(null);
        ProducerRecord<String, String> record = new ProducerRecord<>("statistics", "statistics", "some record data");

        AtomicBoolean hasSendFailed = new AtomicBoolean(false);

        // When
        stroomKafkaProducer.send(record, StroomKafkaProducer.FlushMode.FLUSH_ON_SEND, ex -> hasSendFailed.set(true));

        // Then
        Assert.assertTrue(hasSendFailed.get());
    }
}
