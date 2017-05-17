package stroom.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;

import java.util.Properties;
import java.util.function.Consumer;

/**
 * A singleton responsible for sending records to Kafka.
 * <p>
 * Requires configuration:
 * <<code>
 * kafka.bootstrap.servers=<host>:<port>
 * </code>
 */
@Component
@Scope(StroomScope.SINGLETON)
public class StroomKafkaProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducer.class);

    private Producer<String, String> producer = null;
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

    public StroomKafkaProducer(
            @Value("#{propertyConfigurer.getProperty('kafka.bootstrap.servers')}") final String bootstrapServers) {
        if (Strings.isNullOrEmpty(bootstrapServers)) {
            LOGGER.error("Kafka is not properly configured: 'kafka.bootstrap.servers' is required.");
        } else {
            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("acks", "all");
            props.put("retries", 0);
            props.put("batch.size", 16384);
            props.put("linger.ms", 1);
            props.put("buffer.memory", 33554432);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            // TODO We will probably want to send Avro'd bytes (or something similar) at some point, so the serializer will need to change.
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

            producer = new KafkaProducer<>(props);
            LOGGER.info("Ready to send records to Kafka.");
        }
    }

    public void send(final ProducerRecord<String, String> record, final Consumer<Exception> exceptionConsumer) {
        if (producer != null) {

            producer.send(record, (recordMetadata, exception) -> {
                if (exception != null) {
                    exceptionConsumer.accept(exception);
                }
                LOGGER.trace("Record send to Kafka");
            });

            producer.flush();
        } else {
            throw new RuntimeException("Producer is null, possibly not correctly configured");
        }
    }

    public void close() {
        producer.close();
    }
}
