package stroom.pipeline.server;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Test;

public class TestStroomKafkaProducer {

    @Test
    public void testManualSend(){
        StroomKafkaProducer stroomKafkaProducer = new StroomKafkaProducer("172.17.0.4:9092");
        ProducerRecord<String, String> record = new ProducerRecord<>("statistics", "statistics", "some record data");
        stroomKafkaProducer.send(record);
        stroomKafkaProducer.close();
    }
}
