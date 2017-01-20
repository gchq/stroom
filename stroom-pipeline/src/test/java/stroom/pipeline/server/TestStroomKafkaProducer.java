package stroom.pipeline.server;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.util.shared.Severity;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TestStroomKafkaProducer {

    @Mock
    private ErrorReceiverProxy errorReceiverProxy;

    @Test
    @Ignore("You may use this to test the local instance of Kafka.")
    public void testManualSend(){
        // Given
        StroomKafkaProducer stroomKafkaProducer = new StroomKafkaProducer("172.17.0.4:9092");
        ProducerRecord<String, String> record = new ProducerRecord<>("statistics", "statistics", "some record data");

        // When
        stroomKafkaProducer.send(record, errorReceiverProxy);

        // Then: manually check your Kafka instances 'statistics' topic for 'some record data'
    }

    @Test
    public void testBadlyConfigured(){
        // Given
        StroomKafkaProducer stroomKafkaProducer = new StroomKafkaProducer(null);
        ProducerRecord<String, String> record = new ProducerRecord<>("statistics", "statistics", "some record data");

        // When
        stroomKafkaProducer.send(record, errorReceiverProxy);

        // Then
        verify(errorReceiverProxy).log(eq(Severity.ERROR), eq(null), eq(null), any(String.class), eq(null));
    }
}
